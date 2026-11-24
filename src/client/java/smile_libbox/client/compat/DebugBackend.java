package smile_libbox.client.compat;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import smile_libbox.api.GameVersion;
import smile_libbox.api.MinecraftVersions;
import smile_libbox.api.debug.Debug;
import smile_libbox.api.ui.Ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 1.20.1~1.21.11（intermediary 命名空间）的 AI 调试后端。
 *
 * <p>所有 {@code net.minecraft} 类型都关在本类里，由库按每个游戏版本单独编译；
 * 使用方只看得到 {@link Debug} 这个纯 Java 门面。会变签名的输入事件统一走
 * {@link McCalls}（Stonecutter 按 1.21.9 分界），本类只描述「要做什么」。</p>
 *
 * <p>动作名与参数都是普通字符串 / JDK 类型，线上加新工具不用改接口；
 * 所有外部输入都先做范围与空值收敛，异常在 {@link Debug#call} 里统一转成结构化错误。</p>
 */
public final class DebugBackend implements Debug.Backend {

	/** 允许的动作名，供使用方做能力发现。 */
	private static final List<String> ACTIONS = List.of(
			"describe", "tick", "screen", "click", "key", "type", "scroll",
			"player", "entities", "world", "block", "inventory", "target",
			"command", "close_screen",
			"place_block", "use_block", "use_item",
			"container", "click_slot", "keybind", "keybinds");

	private long ticks;

	public DebugBackend() {
		// 只做计数：任何「每帧改写 KeyMapping」的写法都会压掉真实键盘状态，
		// 表现是按住前进 / 疾跑时一按跳跃就被清成松开（瞬间刹车）。
		// 玩家操作类动作（虚拟按键、自动走位、自动挖掘）已整体移除，见 README。
		Ui.onTick(() -> this.ticks++);
	}

	@Override
	public Object call(String action, Map<String, Object> args) {
		Map<String, Object> a = args == null ? new LinkedHashMap<>() : args;
		switch (action == null ? "" : action) {
			case "describe":
				return describe();
			case "tick":
				return Debug.ok("ticks", this.ticks);
			case "screen":
				return screen(a);
			case "click":
				return click(a);
			case "key":
				return key(a);
			case "type":
				return typeText(a);
			case "scroll":
				return scroll(a);
			case "player":
				return player(a);
			case "entities":
				return entities(a);
			case "world":
				return world(a);
			case "block":
				return block(a);
			case "inventory":
				return inventory(a);
			case "target":
				return target();
			case "command":
				return command(a);
			case "control":
			case "move":
			case "mine":
			case "job":
				return Debug.error("玩家操作注入已移除：它会在每个 tick 改写 KeyMapping，"
						+ "压掉真实键盘状态（按住前进/疾跑时按跳跃会瞬间刹车）。"
						+ "需要让角色移动请改用原版命令或外部按键注入。");
			case "close_screen":
				Ui.closeScreen(Ui.currentScreen());
				return Debug.ok("closed", true);
			case "place_block":
				return placeBlock(a);
			case "use_block":
				return useBlock(a);
			case "use_item":
				return useItem(a);
			case "container":
				return container(a);
			case "click_slot":
				return clickSlot(a);
			case "keybind":
				return keybind(a);
			case "keybinds":
				return keybinds(a);
			default:
				return Debug.error("未知动作：" + action);
		}
	}

	// ------------------------------------------------------------------ 元信息

	private Object describe() {
		Minecraft mc = Minecraft.getInstance();
		return Debug.ok(
				"namespace", "intermediary",
				"game_version", GameVersion.display(),
				"environment", MinecraftVersions.summary(),
				"actions", new ArrayList<Object>(ACTIONS),
				"client_ready", mc != null,
				"ui_ready", Ui.ready());
	}

	// ------------------------------------------------------------------ 界面 / 控件

	/** 当前界面与全部控件；控件 id 为本次遍历的稳定序号，供 click 回引。 */
	private Object screen(Map<String, Object> a) {
		Object handle = Ui.currentScreen();
		if (!(handle instanceof Screen current)) {
			return Debug.ok("screen", null, "screen_id", "0", "widgets", new ArrayList<>(),
					"hint", "当前没有打开任何界面（在游戏中）");
		}
		int maxDepth = clamp(Debug.integer(a, "max_depth", 6), 0, 16);
		int maxWidgets = clamp(Debug.integer(a, "max_widgets", 200), 1, 1000);
		boolean includeHidden = Debug.bool(a, "include_hidden", true);
		String filter = Debug.str(a, "text_filter", "").trim().toLowerCase(Locale.ROOT);

		List<AbstractWidget> found = new ArrayList<>();
		collectWidgets(current, found, maxDepth, includeHidden, filter, maxWidgets,
				Collections.newSetFromMap(new IdentityHashMap<>()));

		List<Object> widgets = new ArrayList<>();
		for (int i = 0; i < found.size(); i++) {
			widgets.add(widgetInfo(i, found.get(i)));
		}
		return Debug.ok(
				"screen_id", String.valueOf(System.identityHashCode(current)),
				"screen_class", current.getClass().getName(),
				"title", current.getTitle().getString(),
				"width", current.width,
				"height", current.height,
				"widget_count", widgets.size(),
				"truncated", found.size() >= maxWidgets,
				"widgets", widgets);
	}

	private static void collectWidgets(Object node, List<AbstractWidget> out, int depth,
			boolean includeHidden, String filter, int limit, Set<Object> seen) {
		if (node == null || out.size() >= limit || !seen.add(node)) {
			return;
		}
		if (node instanceof AbstractWidget widget) {
			String text = widget.getMessage() == null ? "" : widget.getMessage().getString();
			boolean matches = filter.isEmpty()
					|| text.toLowerCase(Locale.ROOT).contains(filter)
					|| widget.getClass().getSimpleName().toLowerCase(Locale.ROOT).contains(filter);
			if ((includeHidden || widget.visible) && matches) {
				out.add(widget);
			}
		}
		if (depth > 0 && node instanceof ContainerEventHandler container) {
			for (GuiEventListener child : container.children()) {
				collectWidgets(child, out, depth - 1, includeHidden, filter, limit, seen);
				if (out.size() >= limit) {
					break;
				}
			}
		}
	}

	private static Map<String, Object> widgetInfo(int id, AbstractWidget widget) {
		Map<String, Object> info = Debug.ok(
				"id", id,
				"type", widget.getClass().getSimpleName(),
				"text", widget.getMessage() == null ? "" : widget.getMessage().getString(),
				"x", widget.getX(),
				"y", widget.getY(),
				"width", widget.getWidth(),
				"height", widget.getHeight(),
				"active", widget.isActive(),
				"visible", widget.visible,
				"focused", widget.isFocused());
		if (widget instanceof EditBox editBox) {
			info.put("value", editBox.getValue());
		}
		return info;
	}

	/** 点击控件（按 id / 文本 / 坐标）或直接把点击事件交给界面。 */
	private Object click(Map<String, Object> a) {
		Object handle = Ui.currentScreen();
		if (!(handle instanceof Screen current)) {
			return Debug.error("当前没有界面可点击；请先让 AI 打开目标界面");
		}
		String expected = Debug.str(a, "expected_screen_id", "");
		if (!expected.isEmpty() && !expected.equals(String.valueOf(System.identityHashCode(current)))) {
			return Debug.error("界面已经变化，expected_screen_id 不匹配，请重新调用 get_screen");
		}
		int button = clamp(Debug.integer(a, "button", 0), 0, 15);
		String mode = Debug.str(a, "action", "click").toLowerCase(Locale.ROOT);

		AbstractWidget widget = resolveWidget(current, a);
		double x = widget == null
				? Debug.dbl(a, "x", Double.NaN)
				: (Debug.has(a, "x") ? Debug.dbl(a, "x", 0)
						: widget.getX() + widget.getWidth() / 2.0);
		double y = widget == null
				? Debug.dbl(a, "y", Double.NaN)
				: (Debug.has(a, "y") ? Debug.dbl(a, "y", 0)
						: widget.getY() + widget.getHeight() / 2.0);
		if (Double.isNaN(x) || Double.isNaN(y)) {
			return Debug.error("需要 widget_id / text / x+y 之一才能定位点击目标");
		}
		Object target = widget == null ? current : widget;
		if (widget != null && !"release".equals(mode)) {
			((ContainerEventHandler) current).setFocused(widget);
		}

		int times = "double".equals(mode) ? 2 : 1;
		boolean handled = false;
		for (int i = 0; i < times; i++) {
			if ("release".equals(mode)) {
				handled |= McCalls.buttonReleased(target, x, y, button);
			} else {
				handled |= McCalls.buttonClicked(target, x, y, button);
				int holdMs = clamp(Debug.integer(a, "hold_ms", 0), 0, 60_000);
				if (holdMs > 0) {
					int capturedButton = button;
					double cx = x, cy = y;
					Object captured = target;
					Thread release = new Thread(() -> {
						try {
							Thread.sleep(holdMs);
						} catch (InterruptedException ignored) {
							Thread.currentThread().interrupt();
						}
						Ui.runOnMain(() -> McCalls.buttonReleased(captured, cx, cy, capturedButton));
					}, "libbox-click-release");
					release.setDaemon(true);
					release.start();
				} else if (!"press".equals(mode)) {
					handled |= McCalls.buttonReleased(target, x, y, button);
				}
			}
		}
		return Debug.ok(
				"handled", handled,
				"mode", mode,
				"button", button,
				"x", x, "y", y,
				"widget_id", widget == null ? null : resolveWidgetId(current, widget, a),
				"screen_id", String.valueOf(System.identityHashCode(current)));
	}

	private static AbstractWidget resolveWidget(Screen screen, Map<String, Object> a) {
		if (Debug.has(a, "widget_id")) {
			int id = Debug.integer(a, "widget_id", -1);
			List<AbstractWidget> all = new ArrayList<>();
			collectWidgets(screen, all, 8, true, "", 1000,
					Collections.newSetFromMap(new IdentityHashMap<>()));
			return id >= 0 && id < all.size() ? all.get(id) : null;
		}
		String text = Debug.str(a, "text", "").trim();
		if (!text.isEmpty()) {
			boolean exact = Debug.bool(a, "exact", false);
			List<AbstractWidget> all = new ArrayList<>();
			collectWidgets(screen, all, 8, true, "", 1000,
					Collections.newSetFromMap(new IdentityHashMap<>()));
			for (AbstractWidget widget : all) {
				String label = widget.getMessage() == null ? "" : widget.getMessage().getString();
				boolean hit = exact ? label.equals(text)
						: label.toLowerCase(Locale.ROOT).contains(text.toLowerCase(Locale.ROOT));
				if (hit) {
					return widget;
				}
			}
			return null;
		}
		if (Debug.has(a, "x") && Debug.has(a, "y")) {
			double x = Debug.dbl(a, "x", 0), y = Debug.dbl(a, "y", 0);
			List<AbstractWidget> all = new ArrayList<>();
			collectWidgets(screen, all, 8, false, "", 1000,
					Collections.newSetFromMap(new IdentityHashMap<>()));
			for (int i = all.size() - 1; i >= 0; i--) {
				AbstractWidget widget = all.get(i);
				if (x >= widget.getX() && x <= widget.getX() + widget.getWidth()
						&& y >= widget.getY() && y <= widget.getY() + widget.getHeight()) {
					return widget;
				}
			}
		}
		return null;
	}

	private static Integer resolveWidgetId(Screen screen, AbstractWidget widget,
			Map<String, Object> a) {
		if (Debug.has(a, "widget_id")) {
			return Debug.integer(a, "widget_id", -1);
		}
		List<AbstractWidget> all = new ArrayList<>();
		collectWidgets(screen, all, 8, true, "", 1000,
				Collections.newSetFromMap(new IdentityHashMap<>()));
		for (int i = 0; i < all.size(); i++) {
			if (all.get(i) == widget) {
				return i;
			}
		}
		return null;
	}

	// ------------------------------------------------------------------ 键鼠输入

	private Object key(Map<String, Object> a) {
		String keyName = Debug.str(a, "key", "");
		int code = Debug.has(a, "code") ? Debug.integer(a, "code", 0) : resolveKeyCode(keyName);
		int scan = Debug.integer(a, "scan_code", 0);
		int modifiers = Debug.integer(a, "modifiers", 0);
		String action = Debug.str(a, "action", "tap").toLowerCase(Locale.ROOT);
		int repeat = clamp(Debug.integer(a, "repeat", 1), 1, 100);
		String target = Debug.str(a, "target", "auto").toLowerCase(Locale.ROOT);
		if (code <= 0) {
			return Debug.error("无法解析按键：" + keyName + "（可传 code 直接给 GLFW 键码）");
		}
		Object screenHandle = Ui.currentScreen();
		boolean toScreen = "screen".equals(target)
				|| ("auto".equals(target) && screenHandle instanceof Screen);
		if (toScreen) {
			if (!(screenHandle instanceof Screen current)) {
				return Debug.error("target=screen 但当前没有界面");
			}
			boolean handled = false;
			for (int i = 0; i < repeat; i++) {
				if (!"release".equals(action)) {
					handled |= McCalls.keyPressed(current, code, scan, modifiers);
				}
				if (!"press".equals(action)) {
					handled |= McCalls.keyReleased(current, code, scan, modifiers);
				}
			}
			return Debug.ok("handled", handled, "target", "screen", "code", code,
					"action", action, "repeat", repeat);
		}
		KeyMapping mapping = keyMapping(keyName);
		if (mapping == null) {
			return Debug.error("找不到游戏键位：" + keyName);
		}
		boolean down = "press".equals(action);
		mapping.setDown(down);
		if (!"press".equals(action) && !"release".equals(action)) {
			clickMapping(mapping, repeat);
		}
		return Debug.ok("handled", true, "target", "game", "key", mapping.getName(),
				"code", code, "action", action, "repeat", repeat);
	}

	private Object typeText(Map<String, Object> a) {
		Object handle = Ui.currentScreen();
		if (!(handle instanceof Screen current)) {
			return Debug.error("当前没有界面，无法输入文本");
		}
		String text = Debug.str(a, "text", "");
		if (text.isEmpty()) {
			return Debug.ok("typed", 0);
		}
		int modifiers = Debug.integer(a, "modifiers", 0);
		int delay = clamp(Debug.integer(a, "delay_ms", 0), 0, 1000);
		boolean replace = Debug.bool(a, "replace", false);
		boolean replaced = false;
		if (replace && current.getFocused() instanceof EditBox editBox) {
			editBox.setValue(text);
			replaced = true; // 已整段替换，不能再逐字符输入，否则出现重复文本
		}
		if (!replaced) {
			if (delay <= 0) {
				text.codePoints().forEach(cp -> McCalls.charTyped(current, cp, modifiers));
			} else {
			Thread worker = new Thread(() -> {
				for (int cp : text.codePoints().toArray()) {
					try {
						Thread.sleep(delay);
					} catch (InterruptedException ignored) {
						Thread.currentThread().interrupt();
						return;
					}
					Ui.runOnMain(() -> McCalls.charTyped(current, cp, modifiers));
				}
			}, "libbox-type-text");
			worker.setDaemon(true);
			worker.start();
			}
		}
		if (Debug.bool(a, "submit", false)) {
			McCalls.keyPressed(current, 257, 0, modifiers);
			McCalls.keyReleased(current, 257, 0, modifiers);
		}
		return Debug.ok("typed", text.codePointCount(0, text.length()),
				"replace", replace, "delay_ms", delay);
	}

	private Object scroll(Map<String, Object> a) {
		Object handle = Ui.currentScreen();
		if (!(handle instanceof Screen current)) {
			return Debug.error("当前没有界面，无法滚动");
		}
		double x = Debug.dbl(a, "x", current.width / 2.0);
		double y = Debug.dbl(a, "y", current.height / 2.0);
		double deltaY = Debug.dbl(a, "delta", 1.0);
		double deltaX = Debug.dbl(a, "delta_x", 0.0);
		return Debug.ok("handled", McCalls.mouseScrolled(current, x, y, deltaY, deltaX),
				"x", x, "y", y, "delta", deltaY, "delta_x", deltaX);
	}

	// ------------------------------------------------------------------ 实体 / 玩家 / 世界

	private Object player(Map<String, Object> a) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer p = mc == null ? null : mc.player;
		if (p == null || mc.level == null) {
			return Debug.ok("in_world", false, "hint", "尚未进入世界（单人/多人游戏未就绪）");
		}
		int slot = selectedSlot(p);
		Map<String, Object> info = Debug.ok(
				"in_world", true,
				"name", p.getName().getString(),
				"uuid", p.getUUID().toString(),
				"id", p.getId(),
				"position", vec(p.position()),
				"block_position", Debug.map("x", p.blockPosition().getX(),
						"y", p.blockPosition().getY(), "z", p.blockPosition().getZ()),
				"rotation", Debug.map("yaw", p.getYRot(), "pitch", p.getXRot()),
				"velocity", vec(p.getDeltaMovement()),
				"on_ground", p.onGround(),
				"health", p.getHealth(),
				"max_health", p.getMaxHealth(),
				"food", p.getFoodData().getFoodLevel(),
				"saturation", p.getFoodData().getSaturationLevel(),
				"xp_level", p.experienceLevel,
				"xp_progress", p.experienceProgress,
				"xp_total", p.totalExperience,
				"sneaking", p.isShiftKeyDown(),
				"crouching", p.isCrouching(),
				"sprinting", p.isSprinting(),
				"flying", p.getAbilities().flying,
				"creative", p.isCreative(),
				"spectator", p.isSpectator(),
				"game_mode", mc.gameMode == null ? "unknown"
						: String.valueOf(mc.gameMode.getPlayerMode()),
				"dimension", String.valueOf(mc.level.dimension()),
				"selected_slot", slot,
				"held_item", itemInfo(p.getInventory().getItem(slot)),
				"vehicle", p.getVehicle() == null ? null : p.getVehicle().getId(),
				"ticks", this.ticks);
		if (Debug.bool(a, "include_effects", true)) {
			List<Object> effects = new ArrayList<>();
			for (Object effect : p.getActiveEffects()) {
				effects.add(String.valueOf(effect));
			}
			info.put("effects", effects);
		}
		if (Debug.bool(a, "include_inventory", false)) {
			info.put("inventory", inventorySlots(p, 0, 40, false));
		}
		return info;
	}

	private Object entities(Map<String, Object> a) {
		Minecraft mc = Minecraft.getInstance();
		if (mc == null || mc.level == null || mc.player == null) {
			return Debug.ok("in_world", false, "entities", new ArrayList<>());
		}
		double radius = Math.max(0, Debug.dbl(a, "radius", 32));
		int limit = clamp(Debug.integer(a, "limit", 64), 1, 500);
		boolean includePlayer = Debug.bool(a, "include_player", false);
		String filter = Debug.str(a, "type_filter", "").trim().toLowerCase(Locale.ROOT);
		Vec3 origin = mc.player.position();

		List<Map<String, Object>> result = new ArrayList<>();
		for (Entity entity : mc.level.entitiesForRendering()) {
			if (entity == null || (!includePlayer && entity == mc.player)) {
				continue;
			}
			Vec3 pos = entity.position();
			double distance = Math.sqrt((pos.x() - origin.x()) * (pos.x() - origin.x())
					+ (pos.y() - origin.y()) * (pos.y() - origin.y())
					+ (pos.z() - origin.z()) * (pos.z() - origin.z()));
			if (distance > radius) {
				continue;
			}
			String type = String.valueOf(EntityType.getKey(entity.getType()));
			String name = entity.getName().getString();
			if (!filter.isEmpty() && !type.toLowerCase(Locale.ROOT).contains(filter)
					&& !name.toLowerCase(Locale.ROOT).contains(filter)) {
				continue;
			}
			result.add(entityInfo(entity, type, distance));
		}
		result.sort((l, r) -> Double.compare(
				((Number) l.get("distance")).doubleValue(),
				((Number) r.get("distance")).doubleValue()));
		if (result.size() > limit) {
			result = new ArrayList<>(result.subList(0, limit));
		}
		return Debug.ok("in_world", true, "radius", radius, "count", result.size(),
				"entities", result);
	}

	private static Map<String, Object> entityInfo(Entity entity, String type, double distance) {
		Map<String, Object> info = Debug.map(
				"id", entity.getId(),
				"uuid", entity.getUUID().toString(),
				"type", type,
				"name", entity.getName().getString(),
				"distance", distance,
				"position", vec(entity.position()),
				"velocity", vec(entity.getDeltaMovement()),
				"rotation", Debug.map("yaw", entity.getYRot(), "pitch", entity.getXRot()),
				"on_ground", entity.onGround(),
				"sneaking", entity.isShiftKeyDown(),
				"sprinting", entity.isSprinting(),
				"vehicle", entity.getVehicle() == null ? null : entity.getVehicle().getId(),
				"player", entity instanceof Player,
				"living", entity instanceof LivingEntity);
		if (entity instanceof LivingEntity living) {
			info.put("health", living.getHealth());
			info.put("max_health", living.getMaxHealth());
			info.put("armor", living.getArmorValue());
		}
		return info;
	}

	private Object world(Map<String, Object> a) {
		Minecraft mc = Minecraft.getInstance();
		if (mc == null || mc.level == null) {
			return Debug.ok("in_world", false, "hint", "尚未进入世界");
		}
		ClientLevel level = mc.level;
		ClientPacketListener connection = mc.getConnection();
		int loadedChunks = -1;
		try {
			loadedChunks = ((ClientChunkCache) level.getChunkSource()).getLoadedChunksCount();
		} catch (Throwable ignored) {
			// 某些版本把 chunk source 换成了别的类型，忽略这个统计项
		}
		int online = connection == null ? 0 : connection.getOnlinePlayers().size();
		return Debug.ok(
				"in_world", true,
				"dimension", String.valueOf(level.dimension()),
				"day_time", level.getDayTime(),
				"raining", level.isRaining(),
				"thundering", level.isThundering(),
				"loaded_chunks", loadedChunks,
				"players_online", online,
				"singleplayer", mc.isSingleplayer(),
				"ticks", this.ticks);
	}

	private Object block(Map<String, Object> a) {
		Minecraft mc = Minecraft.getInstance();
		if (mc == null || mc.level == null) {
			return Debug.ok("in_world", false);
		}
		int x = Debug.integer(a, "x", 0);
		int y = Debug.integer(a, "y", 0);
		int z = Debug.integer(a, "z", 0);
		BlockPos pos = new BlockPos(x, y, z);
		BlockState state = mc.level.getBlockState(pos);
		return Debug.ok("in_world", true, "x", x, "y", y, "z", z,
				"state", state.toString(),
				"block", state.getBlock().getName().getString());
	}

	private Object inventory(Map<String, Object> a) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer p = mc == null ? null : mc.player;
		if (p == null) {
			return Debug.ok("in_world", false, "items", new ArrayList<>());
		}
		int from = clamp(Debug.integer(a, "slot_min", 0), 0, 40);
		int to = clamp(Debug.integer(a, "slot_max", 35), from, 40);
		boolean includeEmpty = Debug.bool(a, "include_empty", false);
		return Debug.ok("in_world", true, "selected_slot", selectedSlot(p),
				"items", inventorySlots(p, from, to, includeEmpty));
	}

	private static List<Object> inventorySlots(LocalPlayer player, int from, int to,
			boolean includeEmpty) {
		List<Object> items = new ArrayList<>();
		int size = player.getInventory().getContainerSize();
		for (int slot = from; slot <= Math.min(to, size - 1); slot++) {
			ItemStack stack = player.getInventory().getItem(slot);
			if (!includeEmpty && (stack == null || stack.isEmpty())) {
				continue;
			}
			Map<String, Object> entry = itemInfo(stack);
			entry.put("slot", slot);
			items.add(entry);
		}
		return items;
	}

	private static Map<String, Object> itemInfo(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return Debug.map("empty", true);
		}
		return Debug.map("empty", false,
				"count", stack.getCount(),
				"name", stack.getDisplayName().getString());
	}

	private Object target() {
		Minecraft mc = Minecraft.getInstance();
		HitResult hit = mc == null ? null : mc.hitResult;
		if (hit == null) {
			return Debug.ok("type", "MISS");
		}
		Map<String, Object> info = Debug.ok("type", String.valueOf(hit.getType()),
				"location", vec(hit.getLocation()));
		if (hit instanceof BlockHitResult blockHit) {
			BlockPos pos = blockHit.getBlockPos();
			info.put("block", Debug.map("x", pos.getX(), "y", pos.getY(), "z", pos.getZ()));
		}
		if (hit instanceof EntityHitResult entityHit && entityHit.getEntity() != null) {
			Entity entity = entityHit.getEntity();
			info.put("entity", entityInfo(entity,
					String.valueOf(EntityType.getKey(entity.getType())), 0));
		}
		return info;
	}

	// ------------------------------------------------------------------ 命令 / 控制

	private Object command(Map<String, Object> a) {
		String text = Debug.str(a, "text", Debug.str(a, "command", "")).trim();
		if (text.isEmpty()) {
			return Debug.error("text / command 不能为空");
		}
		Minecraft mc = Minecraft.getInstance();
		ClientPacketListener connection = mc == null ? null : mc.getConnection();
		if (connection == null) {
			return Debug.error("尚未连接到世界，无法发送命令");
		}
		boolean asChat = Debug.bool(a, "as_chat", false);
		if (asChat) {
			connection.sendChat(text);
		} else {
			connection.sendCommand(text.startsWith("/") ? text.substring(1) : text);
		}
		return Debug.ok("sent", text, "mode", asChat ? "chat" : "command");
	}

	// ------------------------------------------------------------------ 内部工具

	private static void clickMapping(KeyMapping mapping, int times) {
		if (mapping == null) {
			return;
		}
		try {
			InputConstants.Key key = InputConstants.getKey(mapping.saveString());
			for (int i = 0; i < Math.max(1, times); i++) {
				KeyMapping.click(key);
			}
		} catch (Throwable ignored) {
			// 键名解析失败时只置位，让仍可响应的按键逻辑继续工作
			mapping.setDown(true);
		}
	}

	private static KeyMapping keyMapping(String name) {
		if (name == null) {
			return null;
		}
		Minecraft mc = Minecraft.getInstance();
		if (mc == null) {
			return null;
		}
		Options o = mc.options;
		switch (name.trim().toLowerCase(Locale.ROOT)) {
			case "forward": case "w": return o.keyUp;
			case "back": case "s": return o.keyDown;
			case "left": case "a": return o.keyLeft;
			case "right": case "d": return o.keyRight;
			case "jump": case "space": return o.keyJump;
			case "sneak": case "shift": return o.keyShift;
			case "sprint": case "ctrl": return o.keySprint;
			case "attack": case "left_click": return o.keyAttack;
			case "use": case "right_click": return o.keyUse;
			case "drop": case "q": return o.keyDrop;
			case "inventory": case "e": return o.keyInventory;
			case "swap": case "swap_hands": case "f": return o.keySwapOffhand;
			case "chat": case "t": return o.keyChat;
			case "command": case "slash": return o.keyCommand;
			case "list": case "tab": return o.keyPlayerList;
			default:
				if (name.startsWith("hotbar") || name.startsWith("slot")) {
					String digits = name.replaceAll("[^0-9]", "");
					if (!digits.isEmpty()) {
						int slot = clamp(Integer.parseInt(digits) - 1, 0, 8);
						return o.keyHotbarSlots[slot];
					}
				}
				return null;
		}
	}

	/** 常用键名 → GLFW 键码；也接受 {@code key.keyboard.xxx} 与纯数字。 */
	private static int resolveKeyCode(String name) {
		if (name == null || name.isBlank()) {
			return 0;
		}
		String value = name.trim();
		if (value.startsWith("key.")) {
			try {
				return InputConstants.getKey(value).getValue();
			} catch (RuntimeException ignored) {
				return 0;
			}
		}
		String lower = value.toLowerCase(Locale.ROOT);
		switch (lower) {
			case "escape": case "esc": return 256;
			case "enter": case "return": return 257;
			case "tab": return 258;
			case "backspace": return 259;
			case "insert": return 260;
			case "delete": return 261;
			case "arrow_right": case "right": return 262;
			case "arrow_left": case "left": return 263;
			case "arrow_down": case "down": return 264;
			case "arrow_up": case "up": return 265;
			case "page_up": return 266;
			case "page_down": return 267;
			case "home": return 268;
			case "end": return 269;
			case "space": return 32;
			case "left_shift": case "shift": return 340;
			case "left_control": case "ctrl": return 341;
			case "left_alt": case "alt": return 342;
			default: break;
		}
		if (lower.matches("f([1-9]|1[0-2])")) {
			return 290 + Integer.parseInt(lower.substring(1)) - 1;
		}
		if (value.length() == 1) {
			return Character.toUpperCase(value.charAt(0));
		}
		try {
			return Integer.parseInt(value);
		} catch (NumberFormatException ignored) {
			return 0;
		}
	}

	/** 快捷栏读取：1.21.5 起 {@code selected} 字段变成 getter。 */
	private static int selectedSlot(LocalPlayer player) {
		//? if >=1.21.5 {
		/*return player.getInventory().getSelectedSlot();
		*///?} else {
		return player.getInventory().selected;
		//?}
	}

	private static Map<String, Object> vec(Vec3 value) {
		return value == null ? null
				: Debug.map("x", value.x(), "y", value.y(), "z", value.z());
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}

	private static double clamp(double value, double min, double max) {
		return Math.max(min, Math.min(max, value));
	}

	// ---------------------------------------------- 移动 / 挖掘 / 放置 / 交互 / 容器 / 键位

	private static LocalPlayer requirePlayer(Minecraft mc) {
		return mc == null || mc.level == null || mc.player == null ? null : mc.player;
	}

	private static Object requireNoScreen(Minecraft mc, String what) {
		return mc.screen == null ? null
				: Debug.error("当前有打开的界面（" + mc.screen.getClass().getSimpleName()
						+ "），请先 close_screen 再" + what);
	}

	private static double square(double v) {
		return v * v;
	}

	private static double yawToward(double dx, double dz) {
		return Math.toDegrees(Math.atan2(-dx, dz));
	}

	private static void aimAt(LocalPlayer p, BlockPos pos) {
		double dx = pos.getX() + 0.5 - p.getX();
		double dy = pos.getY() + 0.5 - p.getEyePosition().y();
		double dz = pos.getZ() + 0.5 - p.getZ();
		p.setYRot((float) yawToward(dx, dz));
		double horiz = Math.sqrt(square(dx) + square(dz));
		p.setXRot((float) clamp(-Math.toDegrees(Math.atan2(dy, horiz)), -90, 90));
	}

	/** 解析 face 参数；缺省取「面向玩家一侧」的面。 */
	private static Direction resolveFace(Map<String, Object> a, BlockPos pos, LocalPlayer p) {
		String face = Debug.str(a, "face", "").trim().toLowerCase(Locale.ROOT);
		if (!face.isEmpty()) {
			Direction d = Direction.byName(face);
			if (d == null) {
				d = Direction.byName(face.toUpperCase(Locale.ROOT));
			}
			if (d != null) {
				return d;
			}
		}
		Vec3 center = new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
		Vec3 eye = p.getEyePosition();
		double dx = eye.x() - center.x();
		double dy = eye.y() - center.y();
		double dz = eye.z() - center.z();
		if (Math.abs(dy) >= Math.abs(dx) && Math.abs(dy) >= Math.abs(dz)) {
			return dy >= 0 ? Direction.UP : Direction.DOWN;
		}
		if (Math.abs(dx) >= Math.abs(dz)) {
			return dx >= 0 ? Direction.EAST : Direction.WEST;
		}
		return dz >= 0 ? Direction.SOUTH : Direction.NORTH;
	}

	private static String itemId(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return "";
		}
		try {
			return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
		} catch (Throwable ignored) {
			return stack.getDisplayName().getString();
		}
	}

	private static boolean stackMatches(ItemStack stack, String want) {
		if (stack == null || stack.isEmpty() || want == null || want.isBlank()) {
			return false;
		}
		String w = want.toLowerCase(Locale.ROOT);
		return itemId(stack).toLowerCase(Locale.ROOT).contains(w)
				|| stack.getDisplayName().getString().toLowerCase(Locale.ROOT).contains(w);
	}

	private static void setHeldSlot(LocalPlayer player, int slot) {
		//? if >=1.21.5 {
		/*player.getInventory().setSelectedSlot(slot);
		*///?} else {
		player.getInventory().selected = slot;
		//?}
	}

	/**
	 * 按 slot（快捷栏 1..9）或 item（id / 名称片段）把目标物品切到手上。
	 * 主背包里的物品会通过 SWAP 点击与当前快捷栏位交换。返回 null 表示成功。
	 */
	private static Object selectHeld(Map<String, Object> a, LocalPlayer p) {
		if (Debug.has(a, "slot")) {
			int slot = clamp(Debug.integer(a, "slot", 1), 1, 9) - 1;
			setHeldSlot(p, slot);
			return null;
		}
		String want = Debug.str(a, "item", "").trim();
		if (want.isEmpty()) {
			return null;
		}
		for (int i = 0; i < 9; i++) {
			if (stackMatches(p.getInventory().getItem(i), want)) {
				setHeldSlot(p, i);
				return null;
			}
		}
		for (int i = 9; i < 36; i++) {
			if (stackMatches(p.getInventory().getItem(i), want)) {
				p.inventoryMenu.clicked(i, selectedSlot(p), ClickType.SWAP, p);
				return null;
			}
		}
		return Debug.error("背包里找不到匹配 \"" + want + "\" 的物品（支持 item id / 显示名片段）");
	}

	// ------------------------------------------------------------------ 放置 / 交互

	/**
	 * 在指定空气位置放置方块：face 指向被点击的相邻实心方块（默认自动挑），
	 * 可用 slot / item 先把手上的物品切好，sneak 用于贴着交互方块放置。
	 */
	private Object placeBlock(Map<String, Object> a) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer p = requirePlayer(mc);
		if (p == null) {
			return Debug.error("尚未进入世界，无法放置");
		}
		if (mc.gameMode == null) {
			return Debug.error("游戏模式未就绪，无法放置");
		}
		Object guard = requireNoScreen(mc, "放置方块");
		if (guard != null) {
			return guard;
		}
		int x = Debug.integer(a, "x", 0);
		int y = Debug.integer(a, "y", 0);
		int z = Debug.integer(a, "z", 0);
		BlockPos pos = new BlockPos(x, y, z);
		BlockState current = mc.level.getBlockState(pos);
		if (!current.isAir()) {
			return Debug.error("目标位置不是空气：" + current.getBlock().getName().getString());
		}
		Direction face = neighborFace(a, pos, p);
		BlockPos clicked = pos.relative(face);
		BlockState clickedState = mc.level.getBlockState(clicked);
		if (clickedState.isAir()) {
			return Debug.error("要点击的相邻方块 (" + clicked.getX() + "," + clicked.getY()
					+ "," + clicked.getZ() + ") 是空气，无法放置");
		}
		aimAt(p, clicked);
		Object selectError = selectHeld(a, p);
		if (selectError != null) {
			return selectError;
		}
		Vec3 center = new Vec3(clicked.getX() + 0.5, clicked.getY() + 0.5, clicked.getZ() + 0.5);
		Vec3 point = new Vec3(center.x() + face.getStepX() * 0.5,
				center.y() + face.getStepY() * 0.5, center.z() + face.getStepZ() * 0.5);
		BlockHitResult hit = new BlockHitResult(point, face, clicked, false);
		InteractionHand hand = hand(a);
		boolean sneak = Debug.bool(a, "sneak", false);
		boolean prev = p.isShiftKeyDown();
		if (sneak) {
			p.setShiftKeyDown(true);
		}
		Object result = mc.gameMode.useItemOn(p, hand, hit);
		if (sneak) {
			p.setShiftKeyDown(prev);
		}
		if (!String.valueOf(result).toUpperCase(Locale.ROOT).contains("PASS")) {
			p.swing(hand);
		}
		return Debug.ok("placed_at", Debug.map("x", x, "y", y, "z", z),
				"clicked", Debug.map("x", clicked.getX(), "y", clicked.getY(), "z", clicked.getZ()),
				"face", String.valueOf(face),
				"result", String.valueOf(result),
				"screen", mc.screen == null ? null : mc.screen.getClass().getName());
	}

	/** place 用的相邻面：优先玩家一侧，找不到实心邻居时逐面搜索。 */
	private static Direction neighborFace(Map<String, Object> a, BlockPos pos, LocalPlayer p) {
		String face = Debug.str(a, "face", "").trim().toLowerCase(Locale.ROOT);
		if (!face.isEmpty()) {
			Direction d = Direction.byName(face);
			if (d == null) {
				d = Direction.byName(face.toUpperCase(Locale.ROOT));
			}
			if (d != null) {
				return d;
			}
		}
		Direction preferred = resolveFace(a, pos, p);
		Minecraft mc = Minecraft.getInstance();
		Direction[] order = { preferred, Direction.DOWN, Direction.UP,
				Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST };
		for (Direction d : order) {
			if (!mc.level.getBlockState(pos.relative(d)).isAir()) {
				return d;
			}
		}
		return preferred;
	}

	private static InteractionHand hand(Map<String, Object> a) {
		return "off".equalsIgnoreCase(Debug.str(a, "hand", "main").trim())
				? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
	}

	/** 交互方块：等价于对准方块按右键（打开箱子 / 按钮 / 门等）。 */
	private Object useBlock(Map<String, Object> a) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer p = requirePlayer(mc);
		if (p == null) {
			return Debug.error("尚未进入世界，无法交互");
		}
		if (mc.gameMode == null) {
			return Debug.error("游戏模式未就绪");
		}
		Object guard = requireNoScreen(mc, "交互方块");
		if (guard != null) {
			return guard;
		}
		BlockHitResult hit;
		if (!Debug.has(a, "x") && mc.hitResult instanceof BlockHitResult cross) {
			hit = cross;
		} else if (Debug.has(a, "x")) {
			BlockPos pos = new BlockPos(Debug.integer(a, "x", 0),
					Debug.integer(a, "y", 0), Debug.integer(a, "z", 0));
			Direction dir = resolveFace(a, pos, p);
			Vec3 center = new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
			Vec3 point = new Vec3(center.x() + dir.getStepX() * 0.5,
					center.y() + dir.getStepY() * 0.5, center.z() + dir.getStepZ() * 0.5);
			hit = new BlockHitResult(point, dir, pos, false);
		} else {
			return Debug.error("未指定 x+y+z 且准星没有命中方块");
		}
		if (Debug.bool(a, "look", true) && Debug.has(a, "x")) {
			aimAt(p, hit.getBlockPos());
		}
		InteractionHand hand = hand(a);
		boolean sneak = Debug.bool(a, "sneak", false);
		boolean prev = p.isShiftKeyDown();
		if (sneak) {
			p.setShiftKeyDown(true);
		}
		Object result = mc.gameMode.useItemOn(p, hand, hit);
		if (sneak) {
			p.setShiftKeyDown(prev);
		}
		if (!String.valueOf(result).toUpperCase(Locale.ROOT).contains("PASS")) {
			p.swing(hand);
		}
		BlockPos pos = hit.getBlockPos();
		return Debug.ok("target", Debug.map("x", pos.getX(), "y", pos.getY(), "z", pos.getZ()),
				"face", String.valueOf(hit.getDirection()),
				"result", String.valueOf(result),
				"screen", mc.screen == null ? null : mc.screen.getClass().getName());
	}

	/** 交互物品：对当前手持物品按右键（进食 / 投掷 / 弓弩蓄力单发等）。 */
	private Object useItem(Map<String, Object> a) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer p = requirePlayer(mc);
		if (p == null) {
			return Debug.error("尚未进入世界，无法使用物品");
		}
		if (mc.gameMode == null) {
			return Debug.error("游戏模式未就绪");
		}
		Object guard = requireNoScreen(mc, "使用物品");
		if (guard != null) {
			return guard;
		}
		Object selectError = selectHeld(a, p);
		if (selectError != null) {
			return selectError;
		}
		InteractionHand hand = hand(a);
		Object result = mc.gameMode.useItem(p, hand);
		if (!String.valueOf(result).toUpperCase(Locale.ROOT).contains("PASS")) {
			p.swing(hand);
		}
		return Debug.ok("result", String.valueOf(result),
				"held_item", itemInfo(p.getInventory().getItem(selectedSlot(p))));
	}

	// ------------------------------------------------------------------ 容器 / 背包

	/** 读取当前容器界面（含创造物品栏）的全部槽位与手上的物品。 */
	private Object container(Map<String, Object> a) {
		Object handle = Ui.currentScreen();
		if (!(handle instanceof AbstractContainerScreen<?> cs)) {
			return Debug.ok("open", false, "hint",
					"没有打开的容器界面；用 use_block 点击箱子打开，或 control_player(inventory=true) 打开背包");
		}
		AbstractContainerMenu menu = cs.getMenu();
		int max = clamp(Debug.integer(a, "max_slots", 200), 1, 500);
		List<Object> slots = new ArrayList<>();
		int total = menu.slots.size();
		for (int i = 0; i < total && i < max; i++) {
			Slot slot = menu.slots.get(i);
			ItemStack stack = slot.getItem();
			Map<String, Object> entry = Debug.map("slot_id", i,
					"x", slot.x, "y", slot.y, "item", itemInfo(stack));
			if (stack != null && !stack.isEmpty()) {
				entry.put("item_id", itemId(stack));
			}
			slots.add(entry);
		}
		Map<String, Object> result = Debug.ok("open", true,
				"screen", cs.getClass().getName(),
				"slot_count", total,
				"truncated", total > max,
				"slots", slots,
				"carried", itemInfo(menu.getCarried()));
		if (handle instanceof CreativeModeInventoryScreen
				&& cs.getMenu() instanceof CreativeModeInventoryScreen.ItemPickerMenu picker) {
			int cap = clamp(Debug.integer(a, "max_creative_items", 500), 1, 2000);
			List<Object> items = new ArrayList<>();
			for (int i = 0; i < picker.items.size() && i < cap; i++) {
				ItemStack stack = picker.items.get(i);
				if (stack == null || stack.isEmpty()) {
					continue;
				}
				items.add(Debug.map("index", i, "item_id", itemId(stack),
						"name", stack.getDisplayName().getString(), "count", stack.getCount()));
			}
			result.put("creative_items", items);
			result.put("creative_hint",
					"创造模式：click_slot(creative_item='stone', slot=1) 直接把物品放进背包槽");
		}
		return result;
	}

	/**
	 * 操作槽位：click（拿取/放下）、quick_move（Shift 移动）、swap（与快捷栏交换）、
	 * throw（丢弃）、clone（创造复制）；slot_id=-999 表示点容器外（丢掉手上物品）。
	 * 创造物品栏可用 creative_item 按名字直接把物品放进背包槽。
	 */
	private Object clickSlot(Map<String, Object> a) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer p = requirePlayer(mc);
		if (p == null) {
			return Debug.error("尚未进入世界");
		}
		Object handle = Ui.currentScreen();
		if (!(handle instanceof AbstractContainerScreen<?> cs)) {
			return Debug.error("没有打开的容器/背包界面；先打开再操作槽位");
		}
		AbstractContainerMenu menu = cs.getMenu();
		String creativeItem = Debug.str(a, "creative_item", "").trim();
		if (!creativeItem.isEmpty()) {
			if (!(menu instanceof CreativeModeInventoryScreen.ItemPickerMenu picker)) {
				return Debug.error("creative_item 只在创造模式物品栏可用");
			}
			int found = -1;
			for (int i = 0; i < picker.items.size(); i++) {
				if (stackMatches(picker.items.get(i), creativeItem)) {
					found = i;
					break;
				}
			}
			if (found < 0) {
				return Debug.error("创造物品列表里找不到：" + creativeItem);
			}
			int target = clamp(Debug.integer(a, "slot", selectedSlot(p) + 1), 1, 35) - 1;
			mc.gameMode.handleCreativeModeItemAdd(picker.items.get(found).copy(), target);
			return Debug.ok("gave", itemId(picker.items.get(found)),
					"index", found, "slot", target + 1);
		}
		int slotId = Debug.integer(a, "slot_id", -1);
		if (slotId != -999 && (slotId < 0 || slotId >= menu.slots.size())) {
			return Debug.error("slot_id 越界：" + slotId + "（共 " + menu.slots.size()
					+ " 槽；-999 = 点击容器外）");
		}
		String action = Debug.str(a, "action", "click").toLowerCase(Locale.ROOT);
		int button = clamp(Debug.integer(a, "button", 0), 0, 40);
		ClickType type;
		switch (action) {
			case "quick_move", "shift" -> type = ClickType.QUICK_MOVE;
			case "swap" -> type = ClickType.SWAP;
			case "throw", "drop" -> type = ClickType.THROW;
			case "clone" -> type = ClickType.CLONE;
			case "pick_all" -> type = ClickType.PICKUP_ALL;
			default -> type = ClickType.PICKUP;
		}
		menu.clicked(slotId, button, type, p);
		Slot slot = slotId >= 0 && slotId < menu.slots.size() ? menu.slots.get(slotId) : null;
		return Debug.ok("action", action, "button", button, "slot_id", slotId,
				"slot_item", slot == null ? null : itemInfo(slot.getItem()),
				"carried", itemInfo(menu.getCarried()));
	}

	// ------------------------------------------------------------------ 键位绑定事件

	/**
	 * 通过 KeyMapping 触发键位绑定事件（不碰真实键盘）：
	 * 原版与模组注册的快捷键都会收到 press/click，兼容 Fabric keybinding 事件。
	 */
	private Object keybind(Map<String, Object> a) {
		Minecraft mc = Minecraft.getInstance();
		if (mc == null || mc.options == null) {
			return Debug.error("客户端尚未就绪");
		}
		String query = Debug.str(a, "key", Debug.str(a, "name", "")).trim();
		if (query.isEmpty()) {
			return Debug.error("需要 key 参数（键位名 key.forward / key.keyboard.r / 模组自定义键位；"
					+ "用 get_keybinds 查看全部）");
		}
		KeyMapping mapping = findKeyMapping(query);
		if (mapping == null) {
			return Debug.error("找不到键位绑定：" + query + "（用 get_keybinds 查看全部已注册键位）");
		}
		String action = Debug.str(a, "action", "tap").toLowerCase(Locale.ROOT);
		int repeat = clamp(Debug.integer(a, "repeat", 1), 1, 30);
		InputConstants.Key key = InputConstants.getKey(mapping.saveString());
		if ("release".equals(action)) {
			mapping.setDown(false);
		} else {
			if (Debug.bool(a, "click", true)) {
				for (int i = 0; i < repeat; i++) {
					KeyMapping.click(key);
				}
			}
			mapping.setDown(true);
			if ("tap".equals(action)) {
				Thread release = new Thread(() -> {
					try {
						Thread.sleep(60);
					} catch (InterruptedException ignored) {
						Thread.currentThread().interrupt();
						return;
					}
					Ui.runOnMain(() -> mapping.setDown(false));
				}, "libbox-keybind-release");
				release.setDaemon(true);
				release.start();
			}
		}
		return Debug.ok("key", mapping.getName(),
				"category", String.valueOf(mapping.getCategory()),
				"physical", mapping.saveString(),
				"bound", !mapping.isUnbound(),
				"action", action, "repeat", repeat, "down", mapping.isDown());
	}

	/** 列出全部已注册键位（含模组的），供 AI 发现可触发的快捷键。 */
	private Object keybinds(Map<String, Object> a) {
		Minecraft mc = Minecraft.getInstance();
		if (mc == null || mc.options == null) {
			return Debug.error("客户端尚未就绪");
		}
		String filter = Debug.str(a, "filter", "").trim().toLowerCase(Locale.ROOT);
		List<Object> entries = new ArrayList<>();
		for (KeyMapping m : mc.options.keyMappings) {
			String name = m.getName();
			String category = String.valueOf(m.getCategory());
			if (!filter.isEmpty() && !name.toLowerCase(Locale.ROOT).contains(filter)
					&& !category.toLowerCase(Locale.ROOT).contains(filter)) {
				continue;
			}
			entries.add(Debug.map("key", name, "category", category,
					"physical", m.saveString(), "bound", !m.isUnbound(), "down", m.isDown()));
		}
		return Debug.ok("count", entries.size(), "keybinds", entries,
				"hint", "press_keybind(key=...) 触发任意绑定事件；模组快捷键同样响应");
	}

	/** 按 键位名 / 名称后缀 / 分类 / 实体键（saveString）匹配一个 KeyMapping。 */
	private static KeyMapping findKeyMapping(String query) {
		Minecraft mc = Minecraft.getInstance();
		if (mc == null || mc.options == null) {
			return null;
		}
		for (KeyMapping m : mc.options.keyMappings) {
			if (m.getName().equalsIgnoreCase(query)) {
				return m;
			}
		}
		String q = query.toLowerCase(Locale.ROOT);
		for (KeyMapping m : mc.options.keyMappings) {
			String name = m.getName().toLowerCase(Locale.ROOT);
			if (name.equals(q) || name.endsWith("." + q)) {
				return m;
			}
		}
		try {
			InputConstants.Key want = InputConstants.getKey(query);
			String wantName = want.getName();
			for (KeyMapping m : mc.options.keyMappings) {
				if (m.saveString().equals(wantName)) {
					return m;
				}
			}
		} catch (Throwable ignored) {
			// 非法实体键串，跳过这条路
		}
		return null;
	}
}
