package smile_libbox.api;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import smile_libbox.SmileSLibBox;

import java.util.List;
import java.util.Set;

/**
 * 让使用方模组自己门控 mixin 的基类：把「哪些注入该生效」的判断交给模组，
 * 而不是由转译层替它决定。
 *
 * <p>业务模组继承本类，在 {@link #accepts(String, String)} 里用
 * {@link Conditional#matches(String)} 写自己的规则，然后在 mixin 配置的
 * {@code "plugin"} 字段里填自己的子类名：</p>
 *
 * <pre>{@code
 * public final class MyMixinPlugin extends VersionedMixinPlugin {
 *     protected boolean accepts(String target, String mixin) {
 *         if (mixin.contains(".compat.v1219.")) return matches(">=1.21.9");
 *         if (mixin.contains(".compat.legacy.")) return matches("<1.21.9");
 *         return true;
 *     }
 * }
 * }</pre>
 *
 * <p>条件不满足时该 mixin 类<b>不会被解析</b>，因此即使它引用了当前版本不存在的类型
 * 也不会破坏启动——这与 {@link Conditional} 的延迟加载是同一套思路。基类把异常
 * 全部吞掉并记日志，避免 mixin 极早期阶段因 Loader 未就绪而崩掉。</p>
 */
public abstract class VersionedMixinPlugin implements IMixinConfigPlugin {

	/** 由使用方实现：返回这个 mixin 在当前环境是否应该应用。 */
	protected abstract boolean accepts(String targetClassName, String mixinClassName);

	/** 语法糖，等价于 {@link Conditional#matches(String)}。 */
	protected static boolean matches(String expression) {
		return Conditional.matches(expression);
	}

	@Override
	public final boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
		try {
			return accepts(targetClassName, mixinClassName);
		} catch (Throwable error) {
			SmileSLibBox.LOGGER.warn("门控 {} 时出错，跳过该 mixin：{}", mixinClassName,
					error.toString());
			return false;
		}
	}

	@Override
	public void onLoad(String mixinPackage) {
	}

	@Override
	public String getRefMapperConfig() {
		return null;
	}

	@Override
	public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
	}

	@Override
	public List<String> getMixins() {
		return null;
	}

	@Override
	public void preApply(String targetClassName, ClassNode targetClass,
			String mixinClassName, IMixinInfo mixinInfo) {
	}

	@Override
	public void postApply(String targetClassName, ClassNode targetClass,
			String mixinClassName, IMixinInfo mixinInfo) {
	}
}
