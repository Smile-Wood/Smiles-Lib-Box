package smile_libbox.client.compat;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.text2speech.Narrator;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.NarratorStatus;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.waypoints.ClientWaypointManager;
import net.minecraft.network.chat.Component;
import smile_libbox.api.ui.ConfigScreen;
import smile_libbox.api.ui.ConfigSpec;
import smile_libbox.api.ui.KeyRef;
import smile_libbox.api.ui.Ui;
import smile_libbox.api.ui.UiScreen;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 26.2 的 {@link Ui.Backend}：全部用官方类名（named）编译，不带 refmap。
 *
 * <p>这一代没有 intermediary、没有 {@code KeyBindingHelper}（{@code KeyMapping} 构造时
 * 自动注册），打开界面是 {@code setScreenAndShow}，当前界面由本类自己记录。</p>
 */
public final class UiBackend implements Ui.Backend {

	private final Map<String, Function<Object, Object>> configFactories = new HashMap<>();
	private Object[] contextualCache;
	private boolean contextualResolved;
	private Object currentScreen;
	private String lastServerAddress = "";
	private long lastServerAddressAt;

	// ---------------------------------------------------------------- 文本 / 界面

	@Override
	public String tr(String key, Object... args) {
		return Component.translatable(key, args == null ? new Object[0] : args).getString();
	}

	@Override
	public Object screen(UiScreen spec) {
		return new VersionedScreen(spec);
	}

	@Override
	public void open(Object handle) {
		if (handle == null) {
			this.currentScreen = null;
			Minecraft.getInstance().setScreenAndShow(null);
			return;
		}
		if (handle instanceof Screen) {
			this.currentScreen = handle;
			Minecraft.getInstance().setScreenAndShow((Screen) handle);
		}
	}

	@Override
	public Object currentScreen() {
		// 26.2 把当前界面交给 Gui 管理（Minecraft.setScreenAndShow → gui.setScreen），
		// 原版自己打开的界面也要能被退出按键 / 返回按钮看到，不能只记 Ui.open 设过的值。
		try {
			Minecraft client = Minecraft.getInstance();
			if (client != null && client.gui != null) {
				return client.gui.screen();
			}
		} catch (Throwable ignored) {
			// 启动早期 Gui 还没构造好，退回自己记录的值
		}
		return this.currentScreen;
	}

	@Override
	public void closeScreen(Object handle) {
		if (handle instanceof Screen) {
			((Screen) handle).onClose();
		}
	}

	@Override
	public boolean shouldCloseOnEsc(Object handle) {
		return !(handle instanceof Screen) || ((Screen) handle).shouldCloseOnEsc();
	}

	@Override
	public String screenName(Object handle) {
		return handle == null ? "null" : handle.getClass().getSimpleName();
	}

	@Override
	public Object screenSpec(Object handle) {
		return handle instanceof VersionedScreen ? ((VersionedScreen) handle).spec() : null;
	}

	// ---------------------------------------------------------------- 键位

	@Override
	public Object key(String namespace, String translationKey, int code) {
		// 26.2 起 KeyMapping 在构造时自行注册到全局表，不再需要 KeyBindingHelper
		return new KeyMapping(translationKey, InputConstants.Type.KEYSYM, code,
				KeyMapping.Category.MISC);
	}

	@Override
	public boolean consumeClick(Object handle) {
		return handle instanceof KeyMapping && ((KeyMapping) handle).consumeClick();
	}

	@Override
	public String keyDisplayName(int type, int code) {
		InputConstants.Key key = keyOf(type, code);
		if (key == null) {
			return "?";
		}
		String text = key.getDisplayName().getString();
		if (text == null || text.isBlank() || text.startsWith("key.")) {
			return type == 1 ? "鼠标按键 " + (code + 1) : key.getName();
		}
		return text;
	}

	@Override
	public String keyName(int type, int code) {
		InputConstants.Key key = keyOf(type, code);
		return key == null ? "" : key.getName();
	}

	private static InputConstants.Key keyOf(int type, int code) {
		try {
			return (type == 1 ? InputConstants.Type.MOUSE : InputConstants.Type.KEYSYM)
					.getOrCreate(code);
		} catch (RuntimeException e) {
			return null;
		}
	}

	@Override
	public KeyRef keyFromCode(int type, int code) {
		InputConstants.Key key = keyOf(type, code);
		return key == null ? null
				: new KeyRef(key.getName(), type, code, this.keyDisplayName(type, code));
	}

	@Override
	public KeyRef keyFromName(String name) {
		if (name == null || name.isBlank()) {
			return null;
		}
		String trimmed = name.trim();
		InputConstants.Key key = null;
		try {
			key = InputConstants.getKey(trimmed);
		} catch (RuntimeException ignored) {
			// 落到下面的鼠标键兜底
		}
		if (key == null) {
			key = parseMouseName(trimmed);
		}
		if (key == null) {
			return null;
		}
		int type = key.getType() == InputConstants.Type.MOUSE ? 1 : 0;
		return this.keyFromCode(type, key.getValue());
	}

	private static InputConstants.Key parseMouseName(String name) {
		if (!name.startsWith("key.mouse.")) {
			return null;
		}
		String suffix = name.substring("key.mouse.".length());
		switch (suffix) {
			case "left":
				return InputConstants.Type.MOUSE.getOrCreate(0);
			case "right":
				return InputConstants.Type.MOUSE.getOrCreate(1);
			case "middle":
				return InputConstants.Type.MOUSE.getOrCreate(2);
			default:
				break;
		}
		try {
			int index = Integer.parseInt(suffix) - 1;
			return index >= 0 ? InputConstants.Type.MOUSE.getOrCreate(index) : null;
		} catch (RuntimeException e) {
			return null;
		}
	}

	// ---------------------------------------------------------------- 客户端状态

	@Override
	public boolean narratorOff() {
		return Minecraft.getInstance().options.narrator().get() == NarratorStatus.OFF;
	}

	@Override
	public void narratorOn() {
		Minecraft client = Minecraft.getInstance();
		if (client.options.narrator().get() == NarratorStatus.OFF) {
			client.options.narrator().set(NarratorStatus.ALL);
			client.options.save();
		}
	}

	@Override
	public void narrate(String text, boolean interrupt, float volume) {
		Narrator.getNarrator().say(text, interrupt, volume);
	}

	@Override
	public long windowHandle() {
		return Minecraft.getInstance().getWindow().handle();
	}

	@Override
	public void runOnMain(Runnable task) {
		Minecraft.getInstance().execute(task);
	}

	@Override
	public boolean jumpDown() {
		return Minecraft.getInstance().options.keyJump.isDown();
	}

	@Override
	public boolean playerListDown() {
		return Minecraft.getInstance().options.keyPlayerList.isDown();
	}

	@Override
	public boolean hasExperience() {
		Minecraft client = Minecraft.getInstance();
		return client.gameMode != null && client.gameMode.hasExperience();
	}

	@Override
	public boolean hasVehicle() {
		Minecraft client = Minecraft.getInstance();
		return client.player != null && client.player.getVehicle() != null;
	}

	/**
	 * 26.2 用原版自己的判定：{@code LocalPlayer.jumpableVehicle()} 只有马 / 骆驼这类
	 * {@code PlayerRideableJumping} 才非 {@code null}。椅子、船、矿车一律 {@code null}。
	 *
	 * <p>{@code JumpableVehicleBar} 的构造函数就是
	 * {@code Objects.requireNonNull(minecraft.player.jumpableVehicle())}，
	 * 所以「有没有可跳跃坐骑」必须按这个口径判，绝不能用 {@link #hasVehicle()} 代替。</p>
	 */
	@Override
	public boolean hasJumpableVehicle() {
		Minecraft client = Minecraft.getInstance();
		return client.player != null && client.player.jumpableVehicle() != null;
	}

	@Override
	public boolean hasWaypoints() {
		Object connection = this.connection();
		if (connection instanceof ClientPacketListener) {
			// 26.2 的正式 API：ClientPacketListener#getWaypointManager() → hasWaypoints()
			ClientWaypointManager manager = ((ClientPacketListener) connection).getWaypointManager();
			return manager != null && manager.hasWaypoints();
		}
		if (connection == null) {
			return false;
		}
		try {
			for (Method method : connection.getClass().getMethods()) {
				if (method.getParameterCount() != 0
						|| !method.getReturnType().getName().toLowerCase().contains("waypoint")) {
					continue;
				}
				method.setAccessible(true);
				Object manager = method.invoke(connection);
				if (manager == null) {
					return false;
				}
				for (Method has : manager.getClass().getMethods()) {
					if (has.getParameterCount() == 0 && has.getReturnType() == boolean.class) {
						return Boolean.TRUE.equals(has.invoke(manager));
					}
				}
			}
		} catch (Throwable ignored) {
			return false;
		}
		return false;
	}

	@Override
	public Object connection() {
		return Minecraft.getInstance().getConnection();
	}

	@Override
	public String serverAddress() {
		// 26.2 的 ServerData.ip 与 1.20.1 同名；草稿判定始终以连接地址为准。
		Minecraft client = Minecraft.getInstance();
		String current = "";
		if (client != null) {
			net.minecraft.client.multiplayer.ServerData data = client.getCurrentServer();
			if (data != null && data.ip != null && !data.ip.isBlank()) {
				current = data.ip;
			} else if (client.isLocalServer()) {
				current = "local";
			}
		}
		if (!current.isEmpty()) {
			// 被踢 / 断线时地址可能已清空，而 ChatScreen#removed() 恰在那一刻落盘。
			this.lastServerAddress = current;
			this.lastServerAddressAt = System.currentTimeMillis();
			return current;
		}
		return System.currentTimeMillis() - this.lastServerAddressAt < 60_000L
				? this.lastServerAddress : "";
	}

	@Override
	public String playerName() {
		Minecraft client = Minecraft.getInstance();
		return client.player == null ? "" : client.player.getName().getString();
	}

	@Override
	public String sendCommand(String command) {
		Minecraft client = Minecraft.getInstance();
		if (client == null || client.getConnection() == null) {
			return "offline";
		}
		String text = command == null ? "" : command.trim();
		if (text.startsWith("/")) {
			text = text.substring(1);
		}
		if (text.isEmpty()) {
			return "empty";
		}
		try {
			// 26.2 的 sendCommand 与 1.20.1 同名同签名，同样只发包、不切界面。
			client.getConnection().sendCommand(text);
			return "";
		} catch (Throwable t) {
			return t.getClass().getSimpleName();
		}
	}

	@Override
	public boolean hasCommand(String name) {
		Minecraft client = Minecraft.getInstance();
		if (client == null || client.getConnection() == null || name == null || name.isBlank()) {
			return false;
		}
		try {
			// 26.2 的命令树泛型参数换成了 ClientSuggestionProvider，getChild 用法不变。
			return client.getConnection().getCommands().getRoot().getChild(name) != null;
		} catch (Throwable t) {
			return false;
		}
	}

	@Override
	public String language() {
		try {
			Object manager = Minecraft.getInstance().getLanguageManager();
			for (Method method : manager.getClass().getMethods()) {
				if (method.getParameterCount() == 0 && method.getReturnType() == String.class
						&& method.getName().toLowerCase().contains("selected")) {
					Object value = method.invoke(manager);
					return value == null ? "en" : value.toString();
				}
			}
		} catch (Throwable ignored) {
			// 语言拿不到时按英语处理
		}
		return "en";
	}

	@Override
	public String fit(String value, int maxWidth) {
		Font font = Minecraft.getInstance().font;
		if (maxWidth <= 0 || font.width(value) <= maxWidth) {
			return value;
		}
		return font.plainSubstrByWidth(value, Math.max(8, maxWidth - 6)) + "…";
	}

	@Override
	public int textWidth(String value) {
		return Minecraft.getInstance().font.width(value);
	}

	@Override
	public List<String> wrap(String value, int maxWidth) {
		Font font = Minecraft.getInstance().font;
		List<String> lines = new ArrayList<>();
		if (maxWidth <= 0) {
			lines.add(value);
			return lines;
		}
		StringBuilder current = new StringBuilder();
		for (int i = 0; i < value.length(); ) {
			int cp = value.codePointAt(i);
			String ch = new String(Character.toChars(cp));
			if (current.length() > 0 && font.width(current + ch) > maxWidth) {
				lines.add(current.toString());
				current.setLength(0);
			}
			current.append(ch);
			i += Character.charCount(cp);
		}
		if (current.length() > 0 || lines.isEmpty()) {
			lines.add(current.toString());
		}
		return lines;
	}

	@Override
	public Object[] contextualStates() {
		if (this.contextualResolved) {
			return this.contextualCache;
		}
		this.contextualResolved = true;
		// 26.2 的四状态枚举从 1.21.9 的 Gui$ContextualInfo 搬到了 Hud$ContextualInfo，
		// 常量顺序仍是 EMPTY / EXPERIENCE / LOCATOR / JUMPABLE_VEHICLE。
		// 两个类都找一遍，将来 Mojang 再挪回去也不用改代码。
		for (Class<?> owner : new Class<?>[]{Hud.class, Gui.class}) {
			try {
				for (Class<?> nested : owner.getDeclaredClasses()) {
					if (!nested.isEnum()) {
						continue;
					}
					Object[] constants = nested.getEnumConstants();
					if (constants != null && constants.length == 4) {
						this.contextualCache = constants;
						Named26Hooks.first(owner.getSimpleName() + ".ContextualInfo");
						return this.contextualCache;
					}
				}
			} catch (Throwable ignored) {
				// 某个嵌套类暂时不可解析不应影响其它候选
			}
		}
		return null;
	}

	// ---------------------------------------------------------------- 事件 / 配置

	@Override
	public void onTick(Runnable task) {
		ClientTickEvents.END_CLIENT_TICK.register(client -> task.run());
	}

	@Override
	public void onStarted(Runnable task) {
		ClientLifecycleEvents.CLIENT_STARTED.register(client -> task.run());
	}

	@Override
	public void onJoin(Runnable task) {
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> task.run());
	}

	/** 在线玩家名：走 tab 列表，远处未加载的假人也在里面。 */
	@Override
	public List<String> onlinePlayers() {
		Minecraft client = Minecraft.getInstance();
		if (client == null || client.getConnection() == null) {
			return List.of();
		}
		List<String> names = new ArrayList<>();
		for (PlayerInfo info : client.getConnection().getOnlinePlayers()) {
			if (info == null || info.getProfile() == null) {
				continue;
			}
			// authlib 7+：GameProfile 是 record，取名字用 name()
			String name = info.getProfile().name();
			if (name != null && !name.isBlank()) {
				names.add(name);
			}
		}
		return names;
	}

	@Override
	public Object config(Object parent, ConfigSpec spec) {
		return this.screen(new ConfigScreen(spec, parent));
	}

	@Override
	public void registerConfigScreen(String modId, Function<Object, Object> factory) {
		this.configFactories.put(modId, factory);
	}

	@Override
	public Function<Object, Object> configScreen(String modId) {
		return this.configFactories.get(modId);
	}
}
