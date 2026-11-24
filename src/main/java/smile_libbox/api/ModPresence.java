package smile_libbox.api;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 软依赖探测工具：让适配层在「目标模组可能不存在」的环境里安全判断能力是否存在。
 *
 * <p>跨版本整合包里经常要靠别的模组补齐特性，直接引用它们的类会让没装该模组的环境
 * 直接抛 {@code NoClassDefFoundError}。本类只做只读查询，不加载第三方业务类
 * （{@link #classPresent} 用 {@code initialize = false}），因此可以放心在启动早期调用。</p>
 */
public final class ModPresence {

	/** 这些 id 属于平台自身，不参与「第三方模组」推断。 */
	private static final String[] BUILTIN = {"minecraft", "java", "smile_libbox"};

	private ModPresence() {
	}

	/** 模组是否已加载。 */
	public static boolean loaded(String modId) {
		try {
			return FabricLoader.getInstance().isModLoaded(modId);
		} catch (Throwable ignored) {
			return false;
		}
	}

	/** 类是否存在（不触发静态初始化）。 */
	public static boolean classPresent(String className) {
		try {
			Class.forName(className, false, ModPresence.class.getClassLoader());
			return true;
		} catch (Throwable ignored) {
			return false;
		}
	}

	/** 模组显示名，找不到时退回 id 本身。 */
	public static String displayName(String modId) {
		try {
			return FabricLoader.getInstance().getModContainer(modId)
					.map(container -> container.getMetadata().getName())
					.orElse(modId);
		} catch (Throwable ignored) {
			return modId;
		}
	}

	/** 显示名 + 版本，用于日志与配置界面。 */
	public static String displayWithVersion(String modId) {
		try {
			return FabricLoader.getInstance().getModContainer(modId)
					.map(container -> container.getMetadata().getName() + " "
							+ container.getMetadata().getVersion().getFriendlyString())
					.orElse(modId);
		} catch (Throwable ignored) {
			return modId;
		}
	}

	/**
	 * 按关键词列出可能相关的第三方模组名（启发式，可能误报）。
	 *
	 * <p>id 与名称都参与匹配；平台自带的 id 会被排除，调用方可以再按自己的白名单
	 * 或黑名单过滤（本方法刻意只做「找出来」，不做「该不该算」的判断）。</p>
	 */
	public static List<String> matching(String... keywords) {
		List<String> result = new ArrayList<>();
		try {
			for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
				String id = mod.getMetadata().getId().toLowerCase(Locale.ROOT);
				if (isBuiltin(id)) {
					continue;
				}
				String haystack = id + " " + mod.getMetadata().getName().toLowerCase(Locale.ROOT);
				for (String keyword : keywords) {
					if (haystack.contains(keyword.toLowerCase(Locale.ROOT))) {
						result.add(mod.getMetadata().getName());
						break;
					}
				}
			}
		} catch (Throwable ignored) {
			// 启动早期 Loader 尚未就绪时返回空列表，调用方按「没检测到」处理
		}
		return result;
	}

	/** 是否属于平台自身（含所有 fabric* 子模组）。 */
	public static boolean isBuiltin(String id) {
		if (id.startsWith("fabric")) {
			return true;
		}
		for (String builtin : BUILTIN) {
			if (id.equals(builtin)) {
				return true;
			}
		}
		return false;
	}
}
