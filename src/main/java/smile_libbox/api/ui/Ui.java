package smile_libbox.api.ui;

import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/**
 * 使用方与 Minecraft 之间的唯一耦合面。
 *
 * <p>本类以及 {@link UiCanvas} / {@link UiHost} / {@link UiScreen} / {@link ConfigSpec}
 * 全部只有 Java 原生类型，常量池里没有任何 {@code net.minecraft} 描述符。
 * 真正的 MC 调用由库在 {@link Backend} 实现里完成 —— 库按目标命名空间分别编译，
 * 因此同一个使用方 jar 可以装到 intermediary（1.20.x~1.21.x）与
 * named（26.2+）两种环境。</p>
 *
 * <p>库在客户端入口点里调用 {@link #install(Backend)}；安装之前所有方法都返回
 * 安全的空值，避免启动早期（Mixin 插件阶段）被调到时抛异常。</p>
 */
public final class Ui {

	private static volatile Backend backend;

	/** 库安装之前注册的 per-tick / 客户端启动回调，安装时按注册顺序重放。 */
	private static final List<Runnable> PENDING_TICKS = new java.util.ArrayList<>();
	private static final List<Runnable> PENDING_STARTS = new java.util.ArrayList<>();
	private static final List<Runnable> PENDING_JOINS = new java.util.ArrayList<>();
	private static final List<Runnable> PENDING_READY = new java.util.ArrayList<>();

	private Ui() {
	}

	public static void install(Backend implementation) {
		List<Runnable> ready;
		List<Runnable> ticks;
		List<Runnable> starts;
		List<Runnable> joins;
		synchronized (Ui.class) {
			backend = implementation;
			ready = new java.util.ArrayList<>(PENDING_READY);
			ticks = new java.util.ArrayList<>(PENDING_TICKS);
			starts = new java.util.ArrayList<>(PENDING_STARTS);
			joins = new java.util.ArrayList<>(PENDING_JOINS);
			PENDING_READY.clear();
			PENDING_TICKS.clear();
			PENDING_STARTS.clear();
			PENDING_JOINS.clear();
		}
		// 就绪任务先跑：它们通常会在这里做完整的注册（键位、per-tick 回调），
		// 注册时 backend 已经就位，会直接落到真实实现上。
		for (Runnable task : ready) {
			task.run();
		}
		for (Runnable task : ticks) {
			implementation.onTick(task);
		}
		for (Runnable task : starts) {
			implementation.onStarted(task);
		}
		for (Runnable task : joins) {
			implementation.onJoin(task);
		}
	}

	/**
	 * 在后端就绪后执行一次；已经就绪则立刻执行。
	 *
	 * <p>使用方的客户端入口不保证排在库的入口之后。需要用到键位、界面这些
	 * 依赖真实后端的 API 时，把整段初始化包进本方法，就不会因为顺序不同而静默失效。</p>
	 */
	public static void whenReady(Runnable task) {
		synchronized (Ui.class) {
			if (backend == null) {
				PENDING_READY.add(task);
				return;
			}
		}
		task.run();
	}

	public static boolean ready() {
		return backend != null;
	}

	private static Backend b() {
		return backend == null ? Backend.NOOP : backend;
	}

	// ---------------------------------------------------------------- 文本 / 界面

	public static String tr(String key, Object... args) {
		return b().tr(key, args);
	}

	public static Object screen(UiScreen spec) {
		return b().screen(spec);
	}

	public static void open(Object screenHandle) {
		b().open(screenHandle);
	}

	public static Object currentScreen() {
		return b().currentScreen();
	}

	public static void closeScreen(Object screenHandle) {
		b().closeScreen(screenHandle);
	}

	public static boolean shouldCloseOnEsc(Object screenHandle) {
		return b().shouldCloseOnEsc(screenHandle);
	}

	public static String screenName(Object screenHandle) {
		return b().screenName(screenHandle);
	}

	/** 取回宿主背后挂载的 {@link UiScreen}，用于 {@code instanceof} 之类的判断。 */
	public static Object screenSpec(Object screenHandle) {
		return b().screenSpec(screenHandle);
	}

	// ---------------------------------------------------------------- 键位

	public static Object key(String namespace, String translationKey, int code) {
		return b().key(namespace, translationKey, code);
	}

	public static boolean consumeClick(Object keyHandle) {
		return b().consumeClick(keyHandle);
	}

	/** type：0=键盘 KEYSYM，1=鼠标 MOUSE。 */
	public static String keyDisplayName(int type, int code) {
		return b().keyDisplayName(type, code);
	}

	public static String keyName(int type, int code) {
		return b().keyName(type, code);
	}

	/** 由键码构造一个纯 Java 键描述；{@code type}：0=键盘，1=鼠标。 */
	public static KeyRef keyFromCode(int type, int code) {
		return b().keyFromCode(type, code);
	}

	/** 解析配置里的键名；无法识别返回 {@code null}。 */
	public static KeyRef keyFromName(String name) {
		return b().keyFromName(name);
	}

	// ---------------------------------------------------------------- 客户端状态

	public static boolean narratorOff() {
		return b().narratorOff();
	}

	public static void narratorOn() {
		b().narratorOn();
	}

	public static void narrate(String text, boolean interrupt, float volume) {
		b().narrate(text, interrupt, volume);
	}

	public static long windowHandle() {
		return b().windowHandle();
	}

	public static void runOnMain(Runnable task) {
		b().runOnMain(task);
	}

	public static boolean jumpDown() {
		return b().jumpDown();
	}

	public static boolean playerListDown() {
		return b().playerListDown();
	}

	public static boolean hasExperience() {
		return b().hasExperience();
	}

	public static boolean hasVehicle() {
		return b().hasVehicle();
	}

	/**
	 * 玩家骑的是不是<b>可跳跃坐骑</b>（马、骆驼这类 {@code PlayerRideableJumping}）。
	 *
	 * <p>椅子、船、矿车这些只是「有载具」，{@link #hasVehicle()} 会返回 {@code true}，
	 * 但 {@code LocalPlayer.jumpableVehicle()} 是 {@code null}。26.2 的
	 * {@code JumpableVehicleBar} 构造函数会对这个返回值做 {@code Objects.requireNonNull}，
	 * 一旦在没有可跳跃坐骑时把上下文条切到 JUMPABLE_VEHICLE 就会 Render Frame 崩溃。
	 * 凡是涉及「跳跃蓄力条」的判断都必须用这个方法，别用 {@code hasVehicle()}。</p>
	 */
	public static boolean hasJumpableVehicle() {
		return b().hasJumpableVehicle();
	}

	public static boolean hasWaypoints() {
		return b().hasWaypoints();
	}

	public static Object connection() {
		return b().connection();
	}

	/**
	 * 直接向服务端发一条命令，<b>不打开聊天框、不留下聊天记录</b>。
	 *
	 * <p>{@code command} 不带前导 {@code /}（带了也会被去掉），例如
	 * {@code "player bot_1a2b spawn"}。返回空串表示已交给网络层；否则是失败原因，
	 * 目前有 {@code offline}（没连服务器）、{@code empty}（空命令）与异常类名。
	 * 刻意返回 {@link String}：使用方要能在界面上显示「未连接」，又不必认识任何
	 * MC 异常类型，也不必为了记日志而把 {@code Throwable} 写进常量池。</p>
	 */
	public static String sendCommand(String command) {
		return b().sendCommand(command);
	}

	/**
	 * 服务端是否向本客户端下发了某个命令根节点（例如 Carpet 的 {@code player}）。
	 *
	 * <p>这是「能不能用」最可靠的判断：命令树由服务端按权限下发，没装 Carpet、
	 * Carpet 关掉了假人命令、或当前玩家没有权限时，节点都不存在。主菜单 /
	 * 未连接时一律返回 {@code false}。</p>
	 */
	public static boolean hasCommand(String name) {
		return b().hasCommand(name);
	}

	/**
	 * 当前连接的服务器地址（玩家填写的 {@code ServerData.ip}，例如 {@code mc.example.com:25565}）。
	 *
	 * <p>刻意返回 {@link String}：聊天草稿、跨客户端恢复这类功能都以「连接地址」为判定依据，
	 * 使用方不需要认识 {@code ServerData}。没有 {@code ServerData} 时单机返回 {@code "local"}，
	 * 其余情况（主菜单、Realms 直连等）返回空串。</p>
	 */
	public static String serverAddress() {
		return b().serverAddress();
	}

	/**
	 * 用系统默认浏览器打开一个链接（文档、下载页、赞助页等）。
	 *
	 * <p>纯 Java 实现（{@code java.awt.Desktop}），不经过 MC，因此使用方 jar 里也不会多出
	 * 任何版本相关的引用。没有桌面 / 没有浏览器时静默失败 —— 点按钮不该让界面崩。</p>
	 */
	public static void openLink(String url) {
		if (url == null || url.isBlank()) {
			return;
		}
		try {
			java.awt.Desktop desktop = java.awt.Desktop.getDesktop();
			if (desktop.isSupported(java.awt.Desktop.Action.BROWSE)) {
				desktop.browse(java.net.URI.create(url.trim()));
			}
		} catch (Throwable ignored) {
			// 无头环境 / 没有默认浏览器：静默忽略
		}
	}

	/**
	 * 当前服务器<b>在线玩家名</b>（含 Carpet 假人），取自客户端的玩家列表（tab 列表）。
	 *
	 * <p>这是「进服后校准本地记录」的依据：Carpet 不提供「列出我的假人」的查询，只能拿
	 * 全量在线名单去比对本地记录，把已经不存在的名字删掉。未连接、或玩家信息还没同步
	 * 下来时返回空列表 —— 使用方应当把「空列表」当成「暂时不知道」，不要据此清空记录。</p>
	 */
	public static List<String> onlinePlayers() {
		return b().onlinePlayers();
	}

	public static String playerName() {
		return b().playerName();
	}

	/** 游戏界面语言（{@code zh_cn} 之类）。 */
	public static String language() {
		return b().language();
	}

	/** 当前字体下按像素宽度截断字符串。 */
	public static String fit(String value, int maxWidth) {
		return b().fit(value, maxWidth);
	}

	public static int textWidth(String value) {
		return b().textWidth(value);
	}

	/** {@code Gui.ContextualInfo} 的四个枚举常量（顺序 EMPTY/EXPERIENCE/LOCATOR/JUMPABLE_VEHICLE）。 */
	public static Object[] contextualStates() {
		return b().contextualStates();
	}

	// ---------------------------------------------------------------- 事件 / 配置

	/**
	 * 注册每帧回调。
	 *
	 * <p>Fabric 不保证各模组客户端入口的执行顺序：使用方模组的入口可能排在库前面，
	 * 那时 {@link #install(Backend)} 还没发生，直接派发给 {@link Backend#NOOP}
	 * 会把回调静默扔掉（表现为热键、上下文条、假人全部失效）。所以安装之前的注册
	 * 先入队，安装时按注册顺序重放。</p>
	 */
	public static void onTick(Runnable task) {
		Backend current = backend;
		if (current != null) {
			current.onTick(task);
			return;
		}
		synchronized (Ui.class) {
			if (backend == null) {
				PENDING_TICKS.add(task);
				return;
			}
			current = backend;
		}
		current.onTick(task);
	}

	/** 同 {@link #onTick(Runnable)}：客户端启动回调也允许在库安装前注册。 */
	public static void onStarted(Runnable task) {
		Backend current = backend;
		if (current != null) {
			current.onStarted(task);
			return;
		}
		synchronized (Ui.class) {
			if (backend == null) {
				PENDING_STARTS.add(task);
				return;
			}
			current = backend;
		}
		current.onStarted(task);
	}

	/**
	 * 进入世界（连上服务器）时回调一次，同 {@link #onTick(Runnable)} 允许提前注册。
	 *
	 * <p>假人记录这类「按服务器保存」的数据要在进服后校准：连接建立时玩家信息往往
	 * 还没下发完，回调里应当先延迟几秒再调 {@link #onlinePlayers()}。</p>
	 */
	public static void onJoin(Runnable task) {
		Backend current = backend;
		if (current != null) {
			current.onJoin(task);
			return;
		}
		synchronized (Ui.class) {
			if (backend == null) {
				PENDING_JOINS.add(task);
				return;
			}
			current = backend;
		}
		current.onJoin(task);
	}

	public static Object config(Object parent, ConfigSpec spec) {
		return b().config(parent, spec);
	}

	/** 注册供 ModMenu 使用的配置界面工厂；参数为父界面句柄，返回界面句柄。 */
	public static void registerConfigScreen(String modId, Function<Object, Object> factory) {
		b().registerConfigScreen(modId, factory);
	}

	public static Function<Object, Object> configScreen(String modId) {
		return b().configScreen(modId);
	}

	public static List<String> wrap(String value, int maxWidth) {
		return b().wrap(value, maxWidth);
	}

	// ---------------------------------------------------------------- 后端契约

	public interface Backend {

		Backend NOOP = new Backend() {
			public String tr(String key, Object... args) { return key; }
			public Object screen(UiScreen spec) { return null; }
			public void open(Object h) { }
			public Object currentScreen() { return null; }
			public void closeScreen(Object h) { }
			public boolean shouldCloseOnEsc(Object h) { return true; }
			public String screenName(Object h) { return "?"; }
			public Object screenSpec(Object h) { return null; }
			public Object key(String n, String k, int c) { return null; }
			public boolean consumeClick(Object k) { return false; }
			public String keyDisplayName(int t, int c) { return String.valueOf(c); }
			public String keyName(int t, int c) { return String.valueOf(c); }
			public KeyRef keyFromCode(int t, int c) { return null; }
			public KeyRef keyFromName(String n) { return null; }
			public boolean narratorOff() { return false; }
			public void narratorOn() { }
			public void narrate(String t, boolean i, float v) { }
			public long windowHandle() { return 0L; }
			public void runOnMain(Runnable r) { r.run(); }
			public boolean jumpDown() { return false; }
			public boolean playerListDown() { return false; }
			public boolean hasExperience() { return false; }
			public boolean hasVehicle() { return false; }
			public boolean hasJumpableVehicle() { return false; }
			public boolean hasWaypoints() { return false; }
			public Object connection() { return null; }
			public String sendCommand(String c) { return "offline"; }
			public boolean hasCommand(String n) { return false; }
			public String serverAddress() { return ""; }
			public java.util.List<String> onlinePlayers() { return java.util.List.of(); }
			public String playerName() { return ""; }
			public String language() { return "en"; }
			public String fit(String v, int w) { return v; }
			public int textWidth(String v) { return v.length() * 6; }
			public Object[] contextualStates() { return null; }
			public void onTick(Runnable r) { }
			public void onStarted(Runnable r) { }
			public void onJoin(Runnable r) { }
			public Object config(Object p, ConfigSpec s) { return null; }
			public void registerConfigScreen(String id, Function<Object, Object> f) { }
			public Function<Object, Object> configScreen(String id) { return null; }
			public List<String> wrap(String v, int w) { return Collections.singletonList(v); }
		};

		String tr(String key, Object... args);

		Object screen(UiScreen spec);

		void open(Object screenHandle);

		Object currentScreen();

		void closeScreen(Object screenHandle);

		boolean shouldCloseOnEsc(Object screenHandle);

		String screenName(Object screenHandle);

		Object screenSpec(Object screenHandle);

		Object key(String namespace, String translationKey, int code);

		boolean consumeClick(Object keyHandle);

		String keyDisplayName(int type, int code);

		String keyName(int type, int code);

		KeyRef keyFromCode(int type, int code);

		KeyRef keyFromName(String name);

		boolean narratorOff();

		void narratorOn();

		void narrate(String text, boolean interrupt, float volume);

		long windowHandle();

		void runOnMain(Runnable task);

		boolean jumpDown();

		boolean playerListDown();

		boolean hasExperience();

		boolean hasVehicle();

		boolean hasJumpableVehicle();

		boolean hasWaypoints();

		Object connection();

		String sendCommand(String command);

		boolean hasCommand(String name);

		String serverAddress();

		/** 在线玩家名（含假人）；未连接或信息未同步完时返回空列表。 */
		java.util.List<String> onlinePlayers();

		String playerName();

		String language();

		String fit(String value, int maxWidth);

		int textWidth(String value);

		Object[] contextualStates();

		void onTick(Runnable task);

		void onStarted(Runnable task);

		void onJoin(Runnable task);

		Object config(Object parent, ConfigSpec spec);

		void registerConfigScreen(String modId, Function<Object, Object> factory);

		Function<Object, Object> configScreen(String modId);

		List<String> wrap(String value, int maxWidth);
	}
}
