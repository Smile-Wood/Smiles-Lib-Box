package smile_libbox.api;

import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;
import smile_libbox.SmileSLibBox;

import java.util.Locale;

/**
 * 运行时自判断：由使用方模组自己决定「哪段代码该不该执行」，从而用<b>一个 jar</b> 覆盖所有版本。
 *
 * <p>这是本库最核心的一条约定。跨版本代码不能靠「同一个类里写两套覆写签名」——
 * 1.21.9 的 {@code KeyEvent} / {@code MouseButtonEvent} 在 1.20.1 根本不存在，
 * 一个 class 里也无法同时声明两套同名不同参的覆写。可行而且更简单的做法是：</p>
 *
 * <ol>
 *   <li>把只在某个版本区间可用的实现<b>拆成独立的类</b>，放进各自的包；</li>
 *   <li>主流程只通过<b>字符串类名</b> + 本类提供的条件表达式引用它们；</li>
 *   <li>运行时由模组自己判断版本 / 能力 / 模组是否存在，条件成立才
 *       {@code Class.forName} 并实例化。</li>
 * </ol>
 *
 * <p>JVM 的类加载是延迟的：字符串常量不会产生符号引用，低版本永远不会去解析那些
 * 引用了新版本类型的类，因此也就不会抛 {@code NoClassDefFoundError}。所有版本的
 * 实现可以一起打进同一个 jar，运行时只加载命中的那一份。</p>
 *
 * <p>本类不引用任何 Minecraft 类型，可以在所有版本、服务端与客户端加载。</p>
 *
 * <h2>条件表达式</h2>
 * <ul>
 *   <li>{@code *} 或空：恒真</li>
 *   <li>{@code >=1.21.9} / {@code >1.20.1} / {@code <1.20.2} / {@code <=1.21.8} /
 *       {@code ==1.21.11} / {@code !=1.20.1}：版本比较</li>
 *   <li>{@code mod:vanillabackport} / {@code !mod:xxx}：模组是否加载</li>
 *   <li>{@code class:net.minecraft.client.KeyMapping$Category}：类是否存在（不初始化）</li>
 *   <li>{@code cap:eventBasedInput}：{@link Compat} 里的固定能力布尔值</li>
 *   <li>{@code datapack:>=15.0} / {@code resourcepack:>=75.0} / {@code protocol:>=763} /
 *       {@code world:>=3465}：按数据包 / 资源包 / 协议 / 世界数据版本比较，
 *       支持 {@code >= > <= < == !=}</li>
 *   <li>{@code &} 连接多个条件表示「全部满足」</li>
 * </ul>
 *
 * <h2>用法</h2>
 * <pre>{@code
 * // 版本相关实现放在独立类里，可以实现 Conditional.Action 或 Runnable：
 * // public final class ModernHooks implements Conditional.Action {
 * //     public void run() { /* 这里可以放心引用 KeyEvent 等新类型 * / }
 * // }
 * Conditional.when(">=1.21.9", "mymod.compat.v1219.ModernHooks");
 *
 * // 二选一（返回稳定接口，业务代码只依赖这个接口）
 * BarHooks hooks = Conditional.choose(BarHooks.class,
 *         ">=1.21.9 => mymod.compat.v1219.ModernBarHooks",
 *         "*        => mymod.compat.legacy.LegacyBarHooks");
 * }</pre>
 */
public final class Conditional {

	/** 一段延迟执行的代码。实现类应当放在独立类里，避免被主流程提前解析。 */
	@FunctionalInterface
	public interface Action {
		void run();
	}

	private static final String SEPARATOR = "=>";

	private Conditional() {
	}

	// ------------------------------------------------------------------
	// 条件判断：结果由模组自己在运行时决定，不需要构建期分支
	// ------------------------------------------------------------------

	/** 判断一个条件表达式在当前环境是否成立。 */
	public static boolean matches(String expression) {
		if (expression == null || expression.isBlank()) {
			return true;
		}
		String value = expression.trim();
		if ("*".equals(value) || "true".equalsIgnoreCase(value)) {
			return true;
		}
		if ("false".equalsIgnoreCase(value)) {
			return false;
		}
		for (String part : value.split("&")) {
			if (!single(part.trim())) {
				return false;
			}
		}
		return true;
	}

	private static boolean single(String rule) {
		if (rule.isEmpty() || "*".equals(rule)) {
			return true;
		}
		if (rule.startsWith("!mod:")) {
			return !ModPresence.loaded(rule.substring(5).trim());
		}
		if (rule.startsWith("mod:")) {
			return ModPresence.loaded(rule.substring(4).trim());
		}
		if (rule.startsWith("class:")) {
			return ModPresence.classPresent(rule.substring(6).trim());
		}
		if (rule.startsWith("cap:")) {
			return capability(rule.substring(4).trim());
		}
		if (rule.startsWith("datapack:")) {
			return packCompare(rule.substring(9).trim(), true);
		}
		if (rule.startsWith("resourcepack:")) {
			return packCompare(rule.substring(13).trim(), false);
		}
		if (rule.startsWith("protocol:")) {
			return numberCompare(rule.substring(9).trim(), MinecraftVersions.protocol());
		}
		if (rule.startsWith("world:")) {
			return numberCompare(rule.substring(6).trim(), MinecraftVersions.world());
		}
		if (rule.startsWith(">=")) {
			return compare(rule.substring(2).trim()) >= 0;
		}
		if (rule.startsWith("<=")) {
			return compare(rule.substring(2).trim()) <= 0;
		}
		if (rule.startsWith("!=")) {
			return compare(rule.substring(2).trim()) != 0;
		}
		if (rule.startsWith("==")) {
			return compare(rule.substring(2).trim()) == 0;
		}
		if (rule.startsWith(">")) {
			return compare(rule.substring(1).trim()) > 0;
		}
		if (rule.startsWith("<")) {
			return compare(rule.substring(1).trim()) < 0;
		}
		SmileSLibBox.LOGGER.warn("无法识别的条件表达式「{}」，按不成立处理", rule);
		return false;
	}

	private static int compare(String version) {
		try {
			return GameVersion.current().compareTo(Version.parse(version));
		} catch (VersionParsingException e) {
			SmileSLibBox.LOGGER.warn("无法解析版本 {}，按不满足处理", version);
			return Integer.MIN_VALUE;
		}
	}

	private static final int GE = 0, GT = 1, LE = 2, LT = 3, EQ = 4, NE = 5;

	private static boolean packCompare(String spec, boolean data) {
		int[] parsed = parseSpec(spec, true);
		if (parsed == null) {
			return false;
		}
		int major = data ? MinecraftVersions.dataPack() : MinecraftVersions.resourcePack();
		int minor = data ? MinecraftVersions.dataPackMinor() : MinecraftVersions.resourcePackMinor();
		if (major == MinecraftVersions.UNKNOWN) {
			return false;
		}
		int comparison = major != parsed[1]
				? Integer.compare(major, parsed[1])
				: Integer.compare(minor, parsed[2]);
		return apply(comparison, parsed[0]);
	}

	private static boolean numberCompare(String spec, int actual) {
		int[] parsed = parseSpec(spec, false);
		if (parsed == null || actual == MinecraftVersions.UNKNOWN) {
			return false;
		}
		return apply(Integer.compare(actual, parsed[1]), parsed[0]);
	}

	/** 解析比较式，返回 {运算符, 主值, 次值}；格式错误返回 null。 */
	private static int[] parseSpec(String spec, boolean allowMinor) {
		String text = spec.trim();
		int op = GE;
		if (text.startsWith(">=")) {
			text = text.substring(2);
		} else if (text.startsWith("<=")) {
			op = LE;
			text = text.substring(2);
		} else if (text.startsWith("!=")) {
			op = NE;
			text = text.substring(2);
		} else if (text.startsWith("==")) {
			op = EQ;
			text = text.substring(2);
		} else if (text.startsWith(">")) {
			op = GT;
			text = text.substring(1);
		} else if (text.startsWith("<")) {
			op = LT;
			text = text.substring(1);
		}
		String[] parts = text.trim().split("\\.");
		try {
			int major = Integer.parseInt(parts[0].trim());
			int minor = allowMinor && parts.length > 1 ? Integer.parseInt(parts[1].trim()) : 0;
			return new int[]{op, major, minor};
		} catch (NumberFormatException error) {
			SmileSLibBox.LOGGER.warn("无法解析版本比较式「{}」，按不成立处理", spec);
			return null;
		}
	}

	private static boolean apply(int comparison, int op) {
		return switch (op) {
			case GE -> comparison >= 0;
			case GT -> comparison > 0;
			case LE -> comparison <= 0;
			case LT -> comparison < 0;
			case EQ -> comparison == 0;
			case NE -> comparison != 0;
			default -> false;
		};
	}

	/** 把 {@link Compat} 的固定能力查询也纳入表达式，便于写成配置 / 规则表。 */
	public static boolean capability(String name) {
		switch (name.toLowerCase(Locale.ROOT).replace("-", "").replace("_", "")) {
			case "fourargbackground":
			case "1202":
				return Compat.fourArgBackground();
			case "datacomponents":
			case "1205":
				return Compat.dataComponents();
			case "resourcelocationfactory":
			case "121":
				return Compat.resourceLocationFactory();
			case "narratorvolume":
			case "1215":
				return Compat.narratorVolume();
			case "eventbasedinput":
			case "contextualbar":
			case "1219":
				return Compat.eventBasedInput();
			default:
				SmileSLibBox.LOGGER.warn("未知的能力名「{}」，按不支持处理", name);
				return false;
		}
	}

	// ------------------------------------------------------------------
	// 延迟加载：只有条件命中才碰那些类
	// ------------------------------------------------------------------

	/**
	 * 条件成立时加载并运行一段代码。实现类需有公开无参构造器，且实现
	 * {@link Action} 或 {@link Runnable}；任何失败都只记日志、不抛出。
	 *
	 * @return 是否确实执行了
	 */
	public static boolean when(String expression, String actionClassName) {
		if (!matches(expression)) {
			return false;
		}
		Object instance = newInstance(actionClassName);
		if (instance instanceof Action action) {
			action.run();
			return true;
		}
		if (instance instanceof Runnable runnable) {
			runnable.run();
			return true;
		}
		if (instance != null) {
			SmileSLibBox.LOGGER.warn("{} 既不是 Conditional.Action 也不是 Runnable，已跳过",
					actionClassName);
		}
		return false;
	}

	/** 无条件加载并运行一段代码（等价于 {@code when("*", ...)}）。 */
	public static boolean run(String actionClassName) {
		return when("*", actionClassName);
	}

	/**
	 * 按规则表挑一个实现，返回<b>稳定接口</b>类型的实例。
	 *
	 * <p>规则格式 {@code "条件表达式 => 完整类名"}，从上往下取第一个条件成立的；
	 * 最后一条通常写 {@code "* => 兜底实现"}。业务代码只依赖接口，不依赖任何版本类型。</p>
	 */
	public static <T> T choose(Class<T> api, String... rules) {
		for (String rule : rules) {
			if (rule == null) {
				continue;
			}
			int split = rule.indexOf(SEPARATOR);
			if (split < 0) {
				SmileSLibBox.LOGGER.warn("规则缺少 '{}' 分隔符：{}", SEPARATOR, rule);
				continue;
			}
			String expression = rule.substring(0, split).trim();
			String className = rule.substring(split + SEPARATOR.length()).trim();
			if (!matches(expression)) {
				continue;
			}
			T value = instantiate(className, api);
			if (value != null) {
				SmileSLibBox.LOGGER.debug("条件「{}」命中，加载 {}", expression, className);
				return value;
			}
			SmileSLibBox.LOGGER.warn("条件「{}」命中但 {} 无法实例化，返回空", expression, className);
			return null;
		}
		SmileSLibBox.LOGGER.warn("没有任何规则命中（{} 条），返回空", rules.length);
		return null;
	}

	/**
	 * 安全实例化：类不存在、构造器抛错、类型不匹配都返回 {@code null} 并记日志。
	 * 这是隔离版本相关类型的关键——调用方只持有稳定接口。
	 */
	public static <T> T instantiate(String className, Class<T> api) {
		Object instance = newInstance(className);
		if (instance == null) {
			return null;
		}
		if (!api.isInstance(instance)) {
			SmileSLibBox.LOGGER.warn("{} 没有实现 {}", className, api.getName());
			return null;
		}
		return api.cast(instance);
	}

	private static Object newInstance(String className) {
		if (className == null || className.isBlank()) {
			return null;
		}
		try {
			Class<?> type = loadClass(className);
			var constructor = type.getDeclaredConstructor();
			constructor.setAccessible(true);
			return constructor.newInstance();
		} catch (Throwable error) {
			// NoClassDefFoundError 也是 Throwable：这正是旧版本加载新实现时的预期路径
			SmileSLibBox.LOGGER.warn("延迟加载 {} 失败：{}", className, error.toString());
			return null;
		}
	}

	/** 优先用加载本库的 ClassLoader，找不到再试线程上下文 Loader（覆盖更多启动阶段）。 */
	private static Class<?> loadClass(String className) throws ClassNotFoundException {
		Throwable primary;
		try {
			return Class.forName(className);
		} catch (Throwable error) {
			primary = error;
		}
		ClassLoader context = Thread.currentThread().getContextClassLoader();
		if (context != null) {
			try {
				return context.loadClass(className);
			} catch (Throwable ignored) {
				// 两个 Loader 都找不到，按「类不存在」处理
			}
		}
		if (primary instanceof RuntimeException runtime) {
			throw runtime;
		}
		if (primary instanceof Error fatal) {
			throw fatal;
		}
		throw new ClassNotFoundException(className, primary);
	}

	/** 便于启动日志的一行摘要：当前版本与各 ABI 能力。 */
	public static String describe() {
		return String.format("mc=%s fourArgBackground=%s dataComponents=%s "
						+ "resourceLocationFactory=%s narratorVolume=%s eventBasedInput=%s",
				GameVersion.display(), Compat.fourArgBackground(), Compat.dataComponents(),
				Compat.resourceLocationFactory(), Compat.narratorVolume(),
				Compat.eventBasedInput());
	}
}
