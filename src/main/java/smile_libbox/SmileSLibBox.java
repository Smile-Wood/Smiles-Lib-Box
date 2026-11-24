package smile_libbox;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import smile_libbox.api.Conditional;
import smile_libbox.api.MinecraftVersions;

/**
 * Smile's Lib Box —— 跨 Minecraft 版本的兼容层前置库。
 *
 * <p>本库只放「同一份源码要跑在多个游戏版本上」所需的适配代码，不含任何业务功能。
 * 其他模组把它作为前置依赖，即可直接使用 {@code smile_libbox.api} 下的统一接口，
 * 不必在每个模组里重复写 Stonecutter 条件编译。</p>
 */
public class SmileSLibBox implements ModInitializer {

	public static final String MOD_ID = "smile_libbox";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		// 启动自检：把版本判断的原始依据打进日志，依赖方排查门控问题时可直接看这两行。
		// 全部走本库的纯 JDK / Loader API，不引入任何 net.minecraft 符号引用。
		LOGGER.info("版本环境：{}", MinecraftVersions.summary());
		LOGGER.info("门控自检：>=1.20={}, datapack:>=15.0={}, resourcepack:>=15.0={}, "
						+ "protocol:>=763={}, world:>=3465={}, mod:minecraft={}",
				Conditional.matches(">=1.20"),
				Conditional.matches("datapack:>=15.0"),
				Conditional.matches("resourcepack:>=15.0"),
				Conditional.matches("protocol:>=763"),
				Conditional.matches("world:>=3465"),
				Conditional.matches("mod:minecraft"));
	}
}
