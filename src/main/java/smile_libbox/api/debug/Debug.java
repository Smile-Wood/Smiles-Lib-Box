package smile_libbox.api.debug;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * AI 调试能力的纯 Java 门面：把「读取游戏状态 / 注入输入」收敛成一次通用调用。
 *
 * <p>与 {@code smile_libbox.api.ui.Ui} 一样，这里不允许出现任何 {@code net.minecraft}
 * 类型：使用方模组（例如 Smile's AI Debuger）只依赖这一个门面，就能保持
 * <b>单 jar、常量池零 MC 引用</b>，同时装到 intermediary（1.20.x~1.21.x）与
 * named（26.2+）两代命名空间。真正碰 MC 类型的实现由库按版本/命名空间编译成
 * {@code smile_libbox.client.compat.DebugBackend}，在客户端入口里
 * {@link #install(Backend)} 进来。</p>
 *
 * <p>为什么只有一个 {@code call} 方法：调试工具集会持续增加，如果每个能力都在接口上
 * 加一个方法，两代适配层和所有版本 jar 都要同步改动。改成「动作名 + 参数 Map」后，
 * 接口永久稳定；参数与返回值只用 JDK 类型（Map / List / String / Number / Boolean），
 * 可直接被 JSON 序列化。新增能力只需在后端加一个 case，接口不用动。</p>
 *
 * <p>后端未安装（启动早期 / 服务端 / 兜底 jar）时所有调用返回
 * {@code {ok=false, error=...}}，绝不抛异常；使用方可以把这条信息直接回给 AI，
 * 让 AI 稍后重试，不需要人类介入。</p>
 */
public final class Debug {

	/** 由库按目标命名空间编译、在客户端入口安装的实现。 */
	@FunctionalInterface
	public interface Backend {
		/** 执行一个动作；返回值必须是可 JSON 序列化的 JDK 类型或 null。 */
		Object call(String action, Map<String, Object> args);
	}

	private static volatile Backend backend;

	private Debug() {
	}

	public static void install(Backend implementation) {
		backend = implementation;
	}

	public static boolean ready() {
		return backend != null;
	}

	public static Backend backend() {
		return backend;
	}

	/**
	 * 执行一个调试动作。后端抛出的任何异常都转成结构化错误，
	 * 以免一次越界参数把游戏主线程或 MCP 线程带崩。
	 */
	public static Object call(String action, Map<String, Object> args) {
		Backend current = backend;
		if (current == null) {
			return error("debug backend 未安装：库 jar 与游戏版本不匹配，或客户端尚未初始化");
		}
		try {
			return current.call(action, args == null ? new LinkedHashMap<>() : args);
		} catch (Throwable t) {
			return error(String.format(Locale.ROOT, "%s 执行失败：%s", action, t));
		}
	}

	// ------------------------------------------------------------------
	// 给使用方 / 后端复用的纯 Java 数据构造与参数读取帮助方法
	// ------------------------------------------------------------------

	/** 由交替的 key/value 构造有序 Map；奇数位会被丢弃，null 值允许。 */
	public static Map<String, Object> map(Object... pairs) {
		Map<String, Object> result = new LinkedHashMap<>();
		if (pairs == null) {
			return result;
		}
		for (int i = 0; i + 1 < pairs.length; i += 2) {
			if (pairs[i] != null) {
				result.put(String.valueOf(pairs[i]), pairs[i + 1]);
			}
		}
		return result;
	}

	public static List<Object> list(Object... values) {
		List<Object> result = new ArrayList<>();
		if (values != null) {
			result.addAll(Arrays.asList(values));
		}
		return result;
	}

	public static Map<String, Object> error(String message) {
		return map("ok", false, "error", message);
	}

	/** 带上 ok=true 的结果 Map。 */
	public static Map<String, Object> ok(Object... pairs) {
		Map<String, Object> result = map(pairs);
		result.put("ok", true);
		return result;
	}

	public static boolean has(Map<String, Object> args, String key) {
		return args != null && args.containsKey(key) && args.get(key) != null;
	}

	public static Object arg(Map<String, Object> args, String key) {
		return args == null ? null : args.get(key);
	}

	public static String str(Map<String, Object> args, String key, String fallback) {
		Object value = arg(args, key);
		return value == null ? fallback : String.valueOf(value);
	}

	public static int integer(Map<String, Object> args, String key, int fallback) {
		Object value = arg(args, key);
		if (value instanceof Number number) {
			return number.intValue();
		}
		if (value instanceof String text) {
			try {
				return Integer.parseInt(text.trim());
			} catch (NumberFormatException ignored) {
				return fallback;
			}
		}
		return fallback;
	}

	public static long lng(Map<String, Object> args, String key, long fallback) {
		Object value = arg(args, key);
		if (value instanceof Number number) {
			return number.longValue();
		}
		if (value instanceof String text) {
			try {
				return Long.parseLong(text.trim());
			} catch (NumberFormatException ignored) {
				return fallback;
			}
		}
		return fallback;
	}

	public static double dbl(Map<String, Object> args, String key, double fallback) {
		Object value = arg(args, key);
		if (value instanceof Number number) {
			return number.doubleValue();
		}
		if (value instanceof String text) {
			try {
				return Double.parseDouble(text.trim());
			} catch (NumberFormatException ignored) {
				return fallback;
			}
		}
		return fallback;
	}

	public static boolean bool(Map<String, Object> args, String key, boolean fallback) {
		Object value = arg(args, key);
		if (value instanceof Boolean flag) {
			return flag;
		}
		if (value instanceof Number number) {
			return number.doubleValue() != 0;
		}
		if (value instanceof String text) {
			String normalized = text.trim().toLowerCase(Locale.ROOT);
			if ("true".equals(normalized) || "1".equals(normalized) || "yes".equals(normalized)) {
				return true;
			}
			if ("false".equals(normalized) || "0".equals(normalized) || "no".equals(normalized)) {
				return false;
			}
		}
		return fallback;
	}

	/** 参数存在且为 true 才返回 true；用于「未提供的键保持原状态」的增量控制。 */
	public static boolean flag(Map<String, Object> args, String key) {
		return bool(args, key, false);
	}
}
