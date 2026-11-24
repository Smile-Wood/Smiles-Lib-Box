package smile_libbox.client.compat;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.NarratorStatus;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * 1.20.1~1.21.11（intermediary 命名空间）的 {@link Ui.Backend} 实现。
 *
 * <p>库按每个游戏版本单独编译本类，因此所有 MC 类型描述符都与目标 jar 一致；
 * 使用方只看得到 {@code smile_libbox.api.ui} 里的纯 Java 接口。</p>
 */
public final class UiBackend implements Ui.Backend {

	private static final Logger LOGGER = LoggerFactory.getLogger("smile_libbox/UiBackend");

	private final Map<String, Function<Object, Object>> configFactories = new HashMap<>();
	private Object[] contextualCache;
	private boolean contextualResolved;
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
		if (handle instanceof Screen) {
			Minecraft.getInstance().setScreen((Screen) handle);
		}
	}

	@Override
	public Object currentScreen() {
		return Minecraft.getInstance().screen;
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
		return KeyBindingHelper.registerKeyBinding(McCalls.keyMapping(namespace, translationKey, code));
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
			if (type == 1) {
				// 原版只给左/右/中键配了翻译，侧键统一按「鼠标按键 N」显示（N 从 1 起）
				return "鼠标按键 " + (code + 1);
			}
			return key.getName();
		}
		return text;
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
			// 名称表里没有，落到下面的鼠标键兜底
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

	// ---------------------------------------------------------------- 客户端状态

	@Override
	public boolean narratorOff() {
		Minecraft client = Minecraft.getInstance();
		return client.options.narrator().get() == NarratorStatus.OFF;
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
		try {
			Object narrator = Class.forName("com.mojang.text2speech.Narrator")
					.getMethod("getNarrator").invoke(null);
			if (narrator == null) {
				return;
			}
			for (Method method : narrator.getClass().getMethods()) {
				if (!method.getName().equals("say")) {
					continue;
				}
				Class<?>[] params = method.getParameterTypes();
				if (params.length >= 2 && params[0] == String.class && params[1] == boolean.class) {
					if (params.length == 2) {
						method.invoke(narrator, text, interrupt);
					} else if (params.length == 3 && params[2] == float.class) {
						method.invoke(narrator, text, interrupt, volume);
					}
					return;
				}
			}
		} catch (Throwable t) {
			LOGGER.debug("讲述人回退调用失败：{}", t.toString());
		}
	}

	@Override
	public long windowHandle() {
		return McCalls.windowHandle(Minecraft.getInstance());
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
	 * 1.20.1~1.21.11 是 intermediary 命名空间，{@code PlayerRideableJumping} 的接口名
	 * 被混淆，不能按名字 instanceof；改成按<b>接口方法形状</b>探测：
	 * 可跳跃坐骑的接口一定同时有 {@code canJump()Z} 与 {@code onPlayerJump(I)V}，
	 * 船 / 矿车 / 家具座椅的接口没有这一组。
	 *
	 * <p>只看类型签名、不调用任何方法，因此不会碰到各版本私有 API；结果按载具类缓存。</p>
	 */
	@Override
	public boolean hasJumpableVehicle() {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.player.getVehicle() == null) {
			return false;
		}
		Class<?> type = client.player.getVehicle().getClass();
		return JUMPABLE_CACHE.computeIfAbsent(type, UiBackend::probeJumpable);
	}

	private static final Map<Class<?>, Boolean> JUMPABLE_CACHE = new HashMap<>();

	private static boolean probeJumpable(Class<?> type) {
		for (Class<?> node = type; node != null && node != Object.class; node = node.getSuperclass()) {
			for (Class<?> iface : node.getInterfaces()) {
				boolean canJump = false;
				boolean playerJump = false;
				for (Method method : iface.getDeclaredMethods()) {
					Class<?>[] args = method.getParameterTypes();
					if (args.length == 0 && method.getReturnType() == boolean.class) {
						canJump = true;
					} else if (args.length == 1 && args[0] == int.class
							&& method.getReturnType() == void.class) {
						playerJump = true;
					}
				}
				if (canJump && playerJump) {
					return true;
				}
			}
		}
		return false;
	}

	@Override
	public boolean hasWaypoints() {
		Object connection = this.connection();
		if (connection == null) {
			return false;
		}
		Method getter = findMethod(connection.getClass(), null, "getWaypointManager");
		if (getter == null) {
			for (Method method : connection.getClass().getMethods()) {
				if (method.getParameterCount() == 0
						&& method.getReturnType().getName().toLowerCase().contains("waypoint")) {
					getter = method;
					break;
				}
			}
		}
		if (getter == null) {
			return false;
		}
		try {
			getter.setAccessible(true);
			Object manager = getter.invoke(connection);
			if (manager == null) {
				return false;
			}
			Method has = findMethod(manager.getClass(), boolean.class, "hasWaypoints");
			if (has == null) {
				for (Method method : manager.getClass().getMethods()) {
					if (method.getParameterCount() == 0 && method.getReturnType() == boolean.class) {
						has = method;
						break;
					}
				}
			}
			return has != null && Boolean.TRUE.equals(has.invoke(manager));
		} catch (Throwable t) {
			return false;
		}
	}

	private static Method findMethod(Class<?> type, Class<?> returnType, String name) {
		for (Method method : type.getMethods()) {
			if (method.getName().equals(name) && method.getParameterCount() == 0
					&& (returnType == null || method.getReturnType() == returnType)) {
				return method;
			}
		}
		return null;
	}

	@Override
	public Object connection() {
		return Minecraft.getInstance().getConnection();
	}

	@Override
	public String serverAddress() {
		// 判定以「玩家填写的连接地址」为准，不用玩家名 / 世界名 / 客户端实例：
		// 换客户端顶号重启后仍是同一个地址，草稿才能命中。
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
			// 被踢 / 断线时 getCurrentServer() 可能已清空，而 ChatScreen#removed()
			// 恰好在那一刻要把草稿落盘，因此短期记住最后一次看到的地址。
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
			// 只发 C2S 命令包：不切界面、不进聊天记录，正好满足「仅发包不开 UI」。
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
			// 命令树由服务端按权限下发：没装 Carpet / 关了假人命令 / 没权限时都拿不到节点。
			return client.getConnection().getCommands().getRoot().getChild(name) != null;
		} catch (Throwable t) {
			// 刚进服务器命令树还没同步、或 brigadier 版本差异都走这里：当作不可用，
			// 绝不在渲染 / tick 线程上抛出去。
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
		} catch (Throwable t) {
			LOGGER.debug("读取游戏语言失败：{}", t.toString());
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
		List<String> result = new ArrayList<>();
		for (FormattedCharSequence line : Minecraft.getInstance().font
				.split(Component.literal(value), maxWidth)) {
			StringBuilder sb = new StringBuilder();
			line.accept((index, style, codePoint) -> {
				sb.appendCodePoint(codePoint);
				return true;
			});
			result.add(sb.toString());
		}
		return result;
	}

	@Override
	public Object[] contextualStates() {
		if (this.contextualResolved) {
			return this.contextualCache;
		}
		this.contextualResolved = true;
		for (Class<?> nested : Gui.class.getDeclaredClasses()) {
			if (!nested.isEnum()) {
				continue;
			}
			Object[] constants = nested.getEnumConstants();
			if (constants != null && constants.length == 4) {
				this.contextualCache = constants;
				return constants;
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

	/**
	 * 在线玩家名：走客户端的玩家列表（tab 列表），而不是世界里的实体 ——
	 * 远处未加载区块的假人也在列表里，实体列表会漏。
	 */
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
			// authlib 7（1.21.11 起）把 GameProfile 改成 record，getName() → name()
			//? if >=1.21.11 {
			/*String name = info.getProfile().name();
			*///?} else {
			String name = info.getProfile().getName();
			//?}
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
