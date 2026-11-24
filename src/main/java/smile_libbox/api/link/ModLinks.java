package smile_libbox.api.link;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 第三方模组联动总线：把「在别的模组里打入注入点」和「谁来决定行为」拆开。
 *
 * <p>库按版本出的 jar 负责所有会随 MC 命名空间变化的注入点；但对<b>第三方模组</b>的
 * 注入点，其目标类名不是 {@code net.minecraft.*}，用字符串软目标 +
 * {@code remap = false} 就能跨命名空间代际复用，因此注入类也可以留在依赖本库的
 * 模组自己的单 jar 里。两种情况下都应让注入类尽量薄：只把原始值交给本总线，
 * 业务判断由使用方在 client / main 入口注册回调。</p>
 *
 * <p>这样带来三个好处：</p>
 * <ol>
 *   <li>注入类不 import 使用方的配置类，改变业务逻辑不需要重新验证 mixin 描述符；</li>
 *   <li>多个依赖本库的模组可以注册到同一个 hook，互不覆盖（按注册顺序取第一个干预值）；</li>
 *   <li>hook id 是字符串，跨 jar、跨命名空间都不会产生编译期符号引用，
 *       这正是「使用方保持单 jar」的前提。</li>
 * </ol>
 *
 * <p>本类只依赖 JDK，不含任何 Minecraft / Fabric 类型，可在 mixin 极早期加载。
 * 所有回调异常都被隔离，单个使用方出错只打印一行日志，不会拖垮第三方模组的方法。</p>
 */
public final class ModLinks {

	/** 返回值覆盖器：返回 {@code null} 表示「不干预，保持第三方模组原本的行为」。 */
	@FunctionalInterface
	public interface Override {
		/**
		 * @param hook     注入点登记的 id
		 * @param original 第三方模组原本要返回的值（可能为 {@code null}）
		 * @return 替换值，类型必须与原值一致；{@code null} 表示放弃干预
		 */
		Object apply(String hook, Object original);
	}

	/** 通知型联动：注入在 void 方法上的钩子用这个，不改返回值。 */
	@FunctionalInterface
	public interface Signal {
		void accept(String hook, Object payload);
	}

	private static final Map<String, List<Override>> OVERRIDES = new ConcurrentHashMap<>();
	private static final Map<String, List<Signal>> SIGNALS = new ConcurrentHashMap<>();

	private ModLinks() {
	}

	/** 注册一个返回值覆盖器。同一 hook 可注册多个，重复注册同一个对象会被忽略。 */
	public static void onOverride(String hook, Override handler) {
		if (hook == null || hook.isBlank() || handler == null) {
			return;
		}
		List<Override> list = OVERRIDES.computeIfAbsent(normalize(hook),
				key -> new CopyOnWriteArrayList<>());
		if (!list.contains(handler)) {
			list.add(handler);
		}
	}

	/** 注册一个通知型监听器。 */
	public static void onSignal(String hook, Signal handler) {
		if (hook == null || hook.isBlank() || handler == null) {
			return;
		}
		List<Signal> list = SIGNALS.computeIfAbsent(normalize(hook),
				key -> new CopyOnWriteArrayList<>());
		if (!list.contains(handler)) {
			list.add(handler);
		}
	}

	/** 某个 hook 是否已有订阅者；配置界面可据此显示「联动已接管」。 */
	public static boolean active(String hook) {
		return subscribers(hook) > 0;
	}

	/** 某个 hook 的订阅者总数（覆盖器 + 监听器）。 */
	public static int subscribers(String hook) {
		if (hook == null) {
			return 0;
		}
		String key = normalize(hook);
		List<Override> overrides = OVERRIDES.get(key);
		List<Signal> signals = SIGNALS.get(key);
		return (overrides == null ? 0 : overrides.size()) + (signals == null ? 0 : signals.size());
	}

	/**
	 * 由薄注入类调用。按注册顺序取第一个非 {@code null} 的返回值；
	 * 全部放弃时返回 {@code null}，注入类据此不调 {@code cir.setReturnValue()}。
	 */
	public static Object dispatch(String hook, Object original) {
		List<Override> list = OVERRIDES.get(normalize(hook));
		if (list == null || list.isEmpty()) {
			return null;
		}
		for (Override handler : list) {
			try {
				Object value = handler.apply(hook, original);
				if (value != null) {
					return value;
				}
			} catch (Throwable error) {
				System.err.println("[smile_libbox] ModLinks 覆盖器异常 hook=" + hook
						+ "：" + error);
			}
		}
		return null;
	}

	/** {@link #dispatch} 的 boolean 便捷版；没有订阅者或全部放弃时返回 {@code null}。 */
	public static Boolean dispatchBoolean(String hook, boolean original) {
		Object value = dispatch(hook, Boolean.valueOf(original));
		return value instanceof Boolean result ? result : null;
	}

	/** 由 void 注入点调用；监听器异常互不影响。 */
	public static void emit(String hook, Object payload) {
		List<Signal> list = SIGNALS.get(normalize(hook));
		if (list == null) {
			return;
		}
		for (Signal handler : list) {
			try {
				handler.accept(hook, payload);
			} catch (Throwable error) {
				System.err.println("[smile_libbox] ModLinks 监听器异常 hook=" + hook
						+ "：" + error);
			}
		}
	}

	/** 启动日志用：一行列出已注册的 hook 与订阅数，便于核对联动是否挂上。 */
	public static String describe() {
		List<String> hooks = new ArrayList<>(OVERRIDES.keySet());
		for (String hook : SIGNALS.keySet()) {
			if (!hooks.contains(hook)) {
				hooks.add(hook);
			}
		}
		Collections.sort(hooks);
		if (hooks.isEmpty()) {
			return "无第三方联动";
		}
		StringBuilder text = new StringBuilder("hooks=").append(hooks.size()).append(" [");
		for (int i = 0; i < hooks.size(); i++) {
			if (i > 0) {
				text.append(", ");
			}
			String hook = hooks.get(i);
			text.append(hook).append('(').append(subscribers(hook)).append(')');
		}
		return text.append(']').toString();
	}

	private static String normalize(String hook) {
		return hook.trim().toLowerCase(Locale.ROOT);
	}
}
