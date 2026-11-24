package smile_libbox.api;

import java.util.Arrays;

/**
 * 软目标（第三方模组）mixin 的门控基类：把「装没装那个模组」的判断统一掉。
 *
 * <p>对第三方模组做注入时，目标类可能根本不存在。若把这类 mixin 放进
 * {@code required: true} / {@code defaultRequire: 1} 的主配置，目标缺失会直接让游戏崩。
 * 正确做法是独立配置 + {@code required: false} + {@code defaultRequire: 0} +
 * {@code "plugin": "你的子类"}，并在解析阶段就跳过不匹配的类。本基类把这套判断写好，
 * 使用方只需覆写 {@link #modId()} 与（可选）{@link #markerClasses()}。</p>
 *
 * <pre>{@code
 * public final class MyBackportPlugin extends SoftTargetMixinPlugin {
 *     protected String modId() { return "vanillabackport"; }
 *     protected String[] markerClasses() {
 *         return new String[]{"com.example.backport.client.LocatorBarRenderer"};
 *     }
 * }
 * }</pre>
 *
 * <p>判断顺序：先用 {@link Conditional#matches(String)} 过版本 / 能力表达式
 * （{@link #when()} 可覆写），再检查模组 id，最后检查标记类。Mixin 极早期 Loader
 * 可能还没注册模组容器，因此标记类同时走 {@link ModPresence#classPresent} 和
 * classpath 资源探测；后者命中即可放行，这与 toolb 原先手写 plugin 的行为一致。</p>
 *
 * <p>{@link #getRefMapperConfig()} 继承 {@link VersionedMixinPlugin} 返回 {@code null}：
 * 软目标用字符串 {@code targets} + {@code remap = false}，本来就不该参与 remap。
 * 本类同样不含任何 Minecraft 类型，可安全在 mixin 阶段加载。</p>
 */
public abstract class SoftTargetMixinPlugin extends VersionedMixinPlugin {

	/** 目标模组的 id；返回空字符串表示只按 {@link #markerClasses()} 判断。 */
	protected abstract String modId();

	/**
	 * 必须同时存在的类（点分全限定名）。很多模组会按版本拆「特性模块」，
	 * 光看 mod id 不足以确认目标类真的在。默认空数组 = 不检查。
	 */
	protected String[] markerClasses() {
		return new String[0];
	}

	/** 额外的条件表达式，如 {@code ">=1.21.9"} 或 {@code "mod:xxx & cap:eventBasedInput"}。 */
	protected String when() {
		return "*";
	}

	/** 可选的逐 mixin 规则；返回 false 则这个 mixin 不应用。 */
	protected boolean acceptsMixin(String targetClassName, String mixinClassName) {
		return true;
	}

	@Override
	protected final boolean accepts(String targetClassName, String mixinClassName) {
		if (!matches(when())) {
			return false;
		}
		if (!present()) {
			return false;
		}
		return acceptsMixin(targetClassName, mixinClassName);
	}

	@Override
	public void onLoad(String mixinPackage) {
		System.out.println("[smile_libbox] 软目标联动门控已加载：mod=" + modId()
				+ " markers=" + Arrays.toString(markerClasses()));
	}

	private boolean present() {
		String id = modId();
		boolean idRequired = id != null && !id.isBlank();
		boolean loaded = idRequired && ModPresence.loaded(id);
		String[] markers = markerClasses();
		boolean markersOk = true;
		boolean resourceHit = false;
		for (String marker : markers) {
			if (marker == null || marker.isBlank()) {
				continue;
			}
			String resource = marker.replace('.', '/') + ".class";
			boolean found = ModPresence.classPresent(marker);
			boolean onClasspath;
			try {
				onClasspath = getClass().getClassLoader().getResource(resource) != null;
			} catch (Throwable ignored) {
				onClasspath = false;
			}
			resourceHit |= onClasspath;
			markersOk &= found || onClasspath;
		}
		// Loader 未就绪时 isModLoaded 会返回 false，但只要标记类的资源在 classpath 上
		// 就说明目标模组确实装了，放行；否则一律跳过。
		if (idRequired && !loaded && !resourceHit) {
			return false;
		}
		return markersOk;
	}
}
