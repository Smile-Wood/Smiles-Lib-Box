package smile_libbox.api;

import smile_libbox.SmileSLibBox;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 游戏版本 / 数据包版本 / 资源包版本的固定查询接口。
 *
 * <p>数据源是 Minecraft 客户端 jar 根目录下的 {@code version.json}——纯 JDK 资源读取，
 * 不引用任何 Minecraft 类，因此这一份 class 可以装在所有游戏版本上。它同时解决了
 * 一个真实的版本差异：1.21.9 之前 {@code pack_version} 写作
 * {@code {"resource":15,"data":15}}，之后改成
 * {@code {"resource_major":75,"resource_minor":0,"data_major":94,"data_minor":1}}，
 * 本类把它们统一成「主版本 + 次版本」四个整数。</p>
 *
 * <p>结果在第一次调用时解析并缓存；取不到资源包信息时数值返回 {@code -1}，
 * 游戏版本字符串退回 Fabric Loader 报告的 minecraft 版本，不会抛异常。</p>
 */
public final class MinecraftVersions {

	/** 未知的整数值。 */
	public static final int UNKNOWN = -1;

	private static final Pattern PACK_BLOCK =
			Pattern.compile("\"pack_version\"\\s*:\\s*\\{([^}]*)\\}", Pattern.DOTALL);

	private static volatile Info cached;

	private MinecraftVersions() {
	}

	/** 游戏版本 id，例如 {@code 1.20.1} / {@code 1.21.11}。 */
	public static String game() {
		String value = get().id;
		return value.isEmpty() ? GameVersion.display() : value;
	}

	/** 游戏版本显示名（快照版可能与 id 不同）。 */
	public static String name() {
		String value = get().name;
		return value.isEmpty() ? game() : value;
	}

	/** 网络协议版本（protocol version）；未知返回 {@link #UNKNOWN}。 */
	public static int protocol() {
		return get().protocol;
	}

	/** 世界数据版本（world_version / data version）；未知返回 {@link #UNKNOWN}。 */
	public static int world() {
		return get().world;
	}

	/** 数据包格式主版本；1.21.9 之前是 {@code data}，之后是 {@code data_major}。 */
	public static int dataPack() {
		return get().dataMajor;
	}

	/** 数据包格式次版本；旧版本没有这个字段，返回 0。 */
	public static int dataPackMinor() {
		return get().dataMinor;
	}

	/** 资源包格式主版本；1.21.9 之前是 {@code resource}，之后是 {@code resource_major}。 */
	public static int resourcePack() {
		return get().resourceMajor;
	}

	/** 资源包格式次版本；旧版本没有这个字段，返回 0。 */
	public static int resourcePackMinor() {
		return get().resourceMinor;
	}

	/** 该版本要求的 Java 主版本；未知返回 {@link #UNKNOWN}。 */
	public static int java() {
		return get().java;
	}

	/**
	 * 数据包格式版本是否不低于给定主/次版本，例如 {@code dataPackAtLeast(15, 0)}。
	 * 资源包 / 数据包相关逻辑用它判断，比硬编码游戏版本更准（快照版、backport 都适用）。
	 */
	public static boolean dataPackAtLeast(int major, int minor) {
		Info value = get();
		return packAtLeast(value.dataMajor, value.dataMinor, major, minor);
	}

	/** 资源包格式版本是否不低于给定主/次版本。 */
	public static boolean resourcePackAtLeast(int major, int minor) {
		Info value = get();
		return packAtLeast(value.resourceMajor, value.resourceMinor, major, minor);
	}

	/** 协议版本是否不低于给定值；未知返回 false。 */
	public static boolean protocolAtLeast(int protocolVersion) {
		int value = protocol();
		return value != UNKNOWN && value >= protocolVersion;
	}

	/** 世界数据版本是否不低于给定值；未知返回 false。 */
	public static boolean worldAtLeast(int worldVersion) {
		int value = world();
		return value != UNKNOWN && value >= worldVersion;
	}

	private static boolean packAtLeast(int actualMajor, int actualMinor, int major, int minor) {
		if (actualMajor == UNKNOWN) {
			return false;
		}
		return actualMajor > major || (actualMajor == major && actualMinor >= minor);
	}

	/** 是否成功读到了 {@code version.json}。 */
	public static boolean available() {
		return get().found;
	}

	/** 便于日志输出的一行摘要。 */
	public static String summary() {
		Info value = get();
		return value.found
				? String.format("mc=%s protocol=%d world=%d data=%d.%d resource=%d.%d java=%d",
						game(), value.protocol, value.world, value.dataMajor, value.dataMinor,
						value.resourceMajor, value.resourceMinor, value.java)
				: "mc=" + game() + " (version.json 不可用)";
	}

	private static Info get() {
		Info value = cached;
		if (value == null) {
			value = load();
			cached = value;
		}
		return value;
	}

	private static Info load() {
		String expected = GameVersion.display();
		try {
			Enumeration<URL> resources = MinecraftVersions.class.getClassLoader()
					.getResources("version.json");
			Info fallback = null;
			while (resources.hasMoreElements()) {
				URL url = resources.nextElement();
				String json = read(url);
				if (json == null || !json.contains("\"id\"")) {
					continue;
				}
				Info info = parse(json);
				if (!info.found) {
					continue;
				}
				// classpath 上可能有别的 jar 也带 version.json；优先取与 Loader 报告一致的那份
				if (info.id.equals(expected)) {
					return info;
				}
				if (fallback == null) {
					fallback = info;
				}
			}
			if (fallback != null) {
				return fallback;
			}
		} catch (IOException | RuntimeException error) {
			SmileSLibBox.LOGGER.warn("读取 version.json 失败，版本数值将返回 UNKNOWN：{}", error.toString());
		}
		return new Info(false, expected, "", UNKNOWN, UNKNOWN, UNKNOWN, UNKNOWN, 0, 0, UNKNOWN);
	}

	private static String read(URL url) throws IOException {
		try (InputStream stream = url.openStream()) {
			return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	private static Info parse(String json) {
		String id = string(json, "id");
		if (id.isEmpty()) {
			return new Info(false, GameVersion.display(), "", UNKNOWN, UNKNOWN,
					UNKNOWN, UNKNOWN, 0, 0, UNKNOWN);
		}
		String name = string(json, "name");
		int protocol = integer(json, "protocol_version");
		int world = integer(json, "world_version");
		int java = integer(json, "java_version");

		int resourceMajor = UNKNOWN;
		int resourceMinor = 0;
		int dataMajor = UNKNOWN;
		int dataMinor = 0;
		Matcher pack = PACK_BLOCK.matcher(json);
		if (pack.find()) {
			String block = pack.group(1);
			// 新结构优先；旧结构没有 *_major 字段
			resourceMajor = firstDefined(block, "resource_major", "resource");
			resourceMinor = Math.max(0, integer(block, "resource_minor"));
			dataMajor = firstDefined(block, "data_major", "data");
			dataMinor = Math.max(0, integer(block, "data_minor"));
		}
		return new Info(true, id, name, protocol, world, dataMajor, dataMinor,
				resourceMajor, resourceMinor, java);
	}

	private static int firstDefined(String json, String preferred, String legacy) {
		int value = integer(json, preferred);
		return value == UNKNOWN ? integer(json, legacy) : value;
	}

	private static String string(String json, String key) {
		Matcher matcher = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"([^\"]*)\"")
				.matcher(json);
		return matcher.find() ? matcher.group(1) : "";
	}

	private static int integer(String json, String key) {
		Matcher matcher = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*(-?\\d+)")
				.matcher(json);
		return matcher.find() ? Integer.parseInt(matcher.group(1)) : UNKNOWN;
	}

	private static final class Info {
		final boolean found;
		final String id;
		final String name;
		final int protocol;
		final int world;
		final int dataMajor;
		final int dataMinor;
		final int resourceMajor;
		final int resourceMinor;
		final int java;

		Info(boolean found, String id, String name, int protocol, int world,
				int dataMajor, int dataMinor, int resourceMajor, int resourceMinor, int java) {
			this.found = found;
			this.id = id;
			this.name = name;
			this.protocol = protocol;
			this.world = world;
			this.dataMajor = dataMajor;
			this.dataMinor = dataMinor;
			this.resourceMajor = resourceMajor;
			this.resourceMinor = resourceMinor;
			this.java = java;
		}
	}
}
