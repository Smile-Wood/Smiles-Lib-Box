package smile_libbox.api;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;
import smile_libbox.SmileSLibBox;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 运行期游戏版本判断（服务端与客户端通用）。
 *
 * <p>多版本工程里有两类差异：</p>
 * <ol>
 *   <li><b>接口差异</b>（方法签名、类名变化）——把对应版本的实现拆到独立类，用
 *       {@link Conditional} 在运行时判断该加载哪一份，主流程只依赖稳定接口；
 *       条件不成立时那些类根本不会被解析，因此一个 jar 就能装下所有版本的实现；</li>
 *   <li><b>行为差异</b>（某个功能只有高版本才有意义）——用本类在运行时判断，
 *       不必为每个版本单独打包一份逻辑。</li>
 * </ol>
 *
 * <p>例如 {@code if (GameVersion.atLeast("1.21")) { ... }}。判断基于 Fabric Loader
 * 报告的 minecraft 模组版本，能正确处理 {@code 1.21.11}、{@code 26.3} 这类版本号。</p>
 */
public final class GameVersion {

	private static volatile Version cached;

	private GameVersion() {
	}

	/** 当前运行的游戏版本；Loader 尚未就绪时退回客户端 jar 的 {@code version.json}。 */
	public static Version current() {
		Version value = cached;
		if (value == null) {
			value = resolve();
			cached = value;
		}
		return value;
	}

	/**
	 * Mixin 配置插件可能在 Loader 完成模组扫描之前就问版本，此时
	 * {@code getModContainer("minecraft")} 还拿不到东西。退回读取 classpath 上的
	 * {@code version.json}（纯 JDK，不依赖 Loader / MC 类），保证「该不该执行」的判断
	 * 在启动任何阶段都可用。
	 */
	private static Version resolve() {
		try {
			var container = FabricLoader.getInstance().getModContainer("minecraft");
			if (container.isPresent()) {
				return container.get().getMetadata().getVersion();
			}
		} catch (Throwable ignored) {
			// Loader 自身尚未初始化：走下面的资源兜底
		}
		String id = versionJsonId();
		if (!id.isEmpty()) {
			try {
				return Version.parse(id);
			} catch (VersionParsingException ignored) {
				// 快照版 id 可能是 25w45a 之类，无法当语义版本解析
			}
		}
		return fallback();
	}

	private static String versionJsonId() {
		try {
			Enumeration<URL> resources = GameVersion.class.getClassLoader().getResources("version.json");
			while (resources.hasMoreElements()) {
				try (InputStream stream = resources.nextElement().openStream()) {
					String json = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
					Matcher matcher = Pattern.compile("\"id\"\\s*:\\s*\"([^\"]*)\"").matcher(json);
					if (matcher.find()) {
						return matcher.group(1);
					}
				}
			}
		} catch (IOException | RuntimeException ignored) {
			// 拿不到就返回空串，由调用方退回 0.0.0
		}
		return "";
	}

	/** 当前版本是否不低于给定版本（{@code atLeast("1.21")} 对 1.21 / 1.21.1 都为真）。 */
	public static boolean atLeast(String version) {
		try {
			return current().compareTo(Version.parse(version)) >= 0;
		} catch (VersionParsingException e) {
			SmileSLibBox.LOGGER.warn("无法解析版本 {}，按不满足处理", version);
			return false;
		}
	}

	/** 当前版本是否低于给定版本。 */
	public static boolean isOlderThan(String version) {
		return !atLeast(version);
	}

	/** 便于日志与界面显示的短版本号。 */
	public static String display() {
		return current().getFriendlyString();
	}

	private static Version fallback() {
		try {
			return Version.parse("0.0.0");
		} catch (VersionParsingException e) {
			throw new IllegalStateException(e);
		}
	}
}
