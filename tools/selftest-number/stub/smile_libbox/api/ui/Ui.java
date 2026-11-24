package smile_libbox.api.ui;

/** 单测桩：只提供 ConfigLayout / ConfigSpec 需要的入口，让界面逻辑脱离 Minecraft 运行。 */
public final class Ui {
	private Ui() {
	}

	public static String tr(String key, Object... args) {
		return key;
	}

	/** 自检里不真的开浏览器，只记下最后一次请求供断言。 */
	public static String lastLink;

	public static void openLink(String url) {
		lastLink = url;
	}
}
