package smile_libbox.api.ui;

import java.util.List;

/**
 * 绘图画布：使用方唯一能碰到的绘图面。
 *
 * <p>接口里只有 Java 原生类型，没有任何 {@code net.minecraft} 描述符，
 * 因此使用方的 class 常量池可以做到零 MC 引用。库按目标命名空间分别实现：
 * 1.20.1~1.21.11 用 {@code GuiGraphics}，26.2 起用 {@code GuiGraphicsExtractor}。</p>
 */
public interface UiCanvas {

	void fill(int x1, int y1, int x2, int y2, int argb);

	void text(String value, int x, int y, int argb, boolean shadow);

	void centered(String value, int x, int y, int argb);

	void scissor(int x1, int y1, int x2, int y2);

	void endScissor();

	/** 字符串像素宽度。 */
	int textWidth(String value);

	/** 按像素宽度截断，尾部加省略号。 */
	String fit(String value, int maxWidth);

	/** 按像素宽度折行，返回逐行文本。 */
	List<String> wrap(String value, int maxWidth);
}
