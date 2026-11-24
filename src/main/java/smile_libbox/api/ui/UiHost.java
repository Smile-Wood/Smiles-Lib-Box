package smile_libbox.api.ui;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * 屏幕宿主：把 {@code Screen} 的尺寸、按钮、关闭/打开等操作收敛成纯 Java 签名。
 *
 * <p>配置界面里所有「有原版材质」的控件（按钮、滑块、输入框）都通过这里创建，
 * 由库按目标版本挂上真正的原版 widget —— 使用方永远不碰贴图坐标，
 * 也就不会出现「自己画色块」那种和游戏观感对不上的界面。</p>
 */
public interface UiHost {

	int width();

	int height();

	/**
	 * 当前 GUI 缩放倍率。
	 *
	 * <p>布局里的右端留白要按它折算：{@code width} 已经是缩放后的逻辑像素，
	 * 只有拿到倍率才能换算出「恒定物理宽度」的边距。</p>
	 */
	double guiScale();

	/** 字符串像素宽度。建控件之前就要量标签列宽，所以放在宿主上而不是画布上。 */
	int textWidth(String value);

	/**
	 * 添加一个原版按钮，返回库内部的控件 id。
	 *
	 * @param label 已经翻译好的文字
	 */
	int button(int x, int y, int width, int height, String label, Runnable onPress);

	void buttonText(int id, String label);

	void buttonEnabled(int id, boolean enabled);

	/**
	 * 通用「可用 / 不可用」开关：按钮与滑块变成原版的灰色禁用态（不响应点击），
	 * 输入框变成不可编辑（文字变暗）。
	 *
	 * <p>配置界面用它实现「前置项关闭时，从属项整体置灰」。</p>
	 */
	void widgetEnabled(int id, boolean enabled);

	/**
	 * 添加一个原版滑块，返回库内部的控件 id。
	 *
	 * @param value 初始值，会被夹到 {@code [min, max]}
	 * @param onChange 拖动时回调，参数是夹取后的整数值
	 */
	int slider(int x, int y, int width, int height, String label, int value, int min, int max,
			IntConsumer onChange);

	/** 外部改值（RESET、配置文件被改）时刷新滑块显示，不触发 {@code onChange}。 */
	void sliderValue(int id, int value);

	/** 移动一个已创建的控件。列表滚动时按行重排就靠它。 */
	void moveWidget(int id, int x, int y);

	/**
	 * 控件的显隐。
	 *
	 * <p>列表把不可见的行藏起来：原版控件由 {@code Screen} 统一绘制，
	 * 不受 {@link UiCanvas#scissor} 约束，滚出可视区后不隐藏就会盖到页签上。</p>
	 */
	void widgetVisible(int id, boolean visible);

	/** 添加一个文本输入框（搜索框等），返回库内部 id。 */
	int textBox(int x, int y, int width, int height, String hint, String initial,
			Consumer<String> onChange);

	String textBoxValue(int id);

	boolean textBoxFocused(int id);

	/** 覆写输入框内容，不触发 responder。 */
	void textBoxValue(int id, String value);

	/** 聚焦 / 取消聚焦输入框，同时改宿主 Screen 的焦点目标（否则键盘事件送不进去）。 */
	void textBoxFocus(int id, boolean focused);

	/**
	 * 建一个专用于整数的输入框：回车时触发 {@code onCommit}，每次内容变化触发
	 * {@code onChange}（用来做实时合法性检测）。
	 *
	 * <p>这里<b>不过滤</b>输入字符：非法的字符必须能真的敲进去，界面层才能把它标红
	 * 并在 tooltip 里说明原因；过滤掉了就永远看不到「为什么不对」。合法性判定由
	 * {@link ConfigSpec.Row#numberError(String)} 统一负责。</p>
	 *
	 * <p>Esc 不在这里处理 —— 由 {@link UiScreen#beforeKey(int)} 在 Screen 抢先关界面之前
	 * 拦下并转成「取消输入」。</p>
	 */
	int numberBox(int x, int y, int width, int height, String initial,
			Consumer<String> onChange, Runnable onCommit);

	/** 旧签名：不需要实时校验时不必传 {@code onChange}。 */
	default int numberBox(int x, int y, int width, int height, String initial,
			Runnable onCommit) {
		return this.numberBox(x, y, width, height, initial, null, onCommit);
	}

	/** 改输入框的文字颜色（非法值标红）。ARGB，原版 {@code EditBox#setTextColor}。 */
	void textBoxColor(int id, int argb);

	/**
	 * 用<b>原版 tooltip</b> 显示几行纯文本。
	 *
	 * <p>不自己画：原版 {@code GuiGraphics} 的 tooltip 会以鼠标为锚点、自动换行、
	 * 自动翻到屏幕内的另一侧，还带原版的背景与边框贴图。宿主按目标版本调用
	 * {@code renderComponentTooltip}（1.20.1~1.21.5）或
	 * {@code setComponentTooltipForNextFrame}（1.21.8+ / 26.2）。</p>
	 *
	 * <p>一帧只保留最后一次请求 —— 与 {@code Screen} 的原版行为一致；坐标通常是
	 * 当前鼠标位置。传空列表等于不显示。</p>
	 */
	default void tooltip(int mouseX, int mouseY, List<String> lines) {
		this.tooltip(mouseX, mouseY, lines, 0);
	}

	/**
	 * 同 {@link #tooltip(int, int, List)}，但可以给整条 tooltip 指定文字颜色。
	 *
	 * <p>{@code argb} 为 0 时用原版默认色（浅灰白）；传 {@code 0xFFFF5555} 就是整条红字 ——
	 * 配置界面用它标出「实时校验没通过」的原因与允许范围。颜色只影响文字，背景与边框
	 * 仍是原版 tooltip 的。</p>
	 */
	default void tooltip(int mouseX, int mouseY, List<String> lines, int argb) {
		if (lines == null || lines.isEmpty()) {
			return;
		}
		List<UiTooltip> tips = new java.util.ArrayList<>(lines.size());
		for (String line : lines) {
			tips.add(UiTooltip.colored(line, argb));
		}
		this.tooltipLines(mouseX, mouseY, tips);
	}

	/**
	 * 逐行给颜色的原版 tooltip。
	 *
	 * <p>一个框里可以有不同颜色的行：禁用行的提示就是「第一行原文说明 + 最后一行红字
	 * 告诉你先开前置项」。宿主只负责按版本把每行变成 {@code Component} 并上色，
	 * 框、换行、跟随鼠标仍由原版绘制。</p>
	 */
	void tooltipLines(int mouseX, int mouseY, List<UiTooltip> lines);

	void clearButtons();

	/** 关闭当前界面，回到构造时给定的父界面。 */
	void close();

	/** 打开另一个由 {@link Ui#screen(UiScreen)} 创建出来的界面句柄。 */
	void open(Object screenHandle);

	String tr(String key, Object... args);
}
