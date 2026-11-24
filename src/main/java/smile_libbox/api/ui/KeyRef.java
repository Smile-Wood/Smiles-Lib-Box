package smile_libbox.api.ui;

/**
 * 一次按键的纯 Java 描述。
 *
 * <p>{@code InputConstants.Key} 在正式环境会被重映射成 {@code net/minecraft/class_XXXX}，
 * 所以不能出现在依赖方的方法描述符里。库按命名空间构造本对象，使用方只存这几个字段。</p>
 */
public final class KeyRef {

	/** 配置里保存的键名，例如 {@code key.keyboard.f13} / {@code key.mouse.4}。 */
	public final String name;

	/** 0 = 键盘 KEYSYM，1 = 鼠标 MOUSE。 */
	public final int type;

	/** GLFW 键码 / 鼠标键的 0 基编号。 */
	public final int code;

	/** 已按当前语言取好的显示文本。 */
	public final String displayName;

	public KeyRef(String name, int type, int code, String displayName) {
		this.name = name;
		this.type = type;
		this.code = code;
		this.displayName = displayName;
	}

	@Override
	public String toString() {
		return this.name;
	}
}
