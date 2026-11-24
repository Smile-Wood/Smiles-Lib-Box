package smile_libbox.client;

import smile_libbox.api.Compat;
import smile_libbox.api.MinecraftVersions;
import smile_libbox.api.VersionedMixinPlugin;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * 库自身 mixin 的安全阀。
 *
 * <p>库按游戏版本分别出 jar，每个 jar 里的 mixin 与 refmap 都是用对应版本 MC 编译的。
 * 正常安装时只装匹配的那个 jar。若用户装错了版本（或用的是不绑定版本的通用兜底 jar），
 * 这里会一律禁用注入，让库只提供固定 API 而不去碰对不上的描述符——结果是功能降级，
 * 而不是启动崩溃。</p>
 *
 * <p>目标版本由构建期写进资源 {@code libbox-target.txt}；运行版本取自客户端
 * {@code version.json}，两者都无需 Loader 或 MC 类，mixin 阶段即可判定。</p>
 */
public final class LibBoxMixinPlugin extends VersionedMixinPlugin {

	private static final boolean MATCHES_TARGET = matchesTarget();

	private static boolean matchesTarget() {
		try (InputStream in = LibBoxMixinPlugin.class.getClassLoader()
				.getResourceAsStream("libbox-target.txt")) {
			if (in == null) {
				return false;
			}
			String target = new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
			if (target.isEmpty() || "*".equals(target) || "generic".equals(target)) {
				return false;
			}
			return target.equals(MinecraftVersions.game());
		} catch (Throwable ignored) {
			return false;
		}
	}

	@Override
	protected boolean accepts(String targetClassName, String mixinClassName) {
		if (!MATCHES_TARGET) {
			return false;
		}
		// 低版本没有 nextContextualInfoState，这个类编译出来是空的，跳过更干净
		if (mixinClassName.endsWith("LibContextualBarMixin")) {
			return Compat.eventBasedInput();
		}
		return true;
	}
}
