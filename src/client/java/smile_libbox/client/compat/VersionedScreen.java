package smile_libbox.client.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import smile_libbox.api.ui.Ui;
import smile_libbox.api.ui.UiCanvas;
import smile_libbox.api.ui.UiHost;
import smile_libbox.api.ui.UiScreen;
import smile_libbox.api.ui.UiTooltip;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntConsumer;

/**
 * 使用方界面在真实 {@link Screen} 上的宿主。
 *
 * <p>1.20.2 / 1.21.9 的签名变化全部由库按版本编译吃下；使用方只实现
 * {@link UiScreen}（纯 Java 接口），常量池零 MC 引用。</p>
 *
 * <p>配置界面要的「原版材质」全部来自这里：按钮是 {@code Button}，滑块是
 * {@link AbstractSliderButton} 的子类，都由 {@code Screen} 统一绘制，
 * 使用方只管给出位置和回调。</p>
 */
public final class VersionedScreen extends Screen implements UiHost {

	private final UiScreen spec;
	private final Map<Integer, AbstractWidget> widgets = new HashMap<>();
	private final Map<Integer, Slider> sliders = new HashMap<>();
	private int nextButtonId = 1;

	/** 本帧要显示的原版 tooltip；由 {@link #tooltip} 记下，render 末尾统一画。 */
	private List<UiTooltip> pendingTipLines;
	private int pendingTipX;
	private int pendingTipY;

	public VersionedScreen(UiScreen spec) {
		super(Component.literal(spec.title()));
		this.spec = spec;
	}

	public UiScreen spec() {
		return this.spec;
	}

	// ------------------------------------------------------------------ UiHost

	@Override
	public int width() {
		return this.width;
	}

	@Override
	public int height() {
		return this.height;
	}

	@Override
	public double guiScale() {
		// 1.20.1 返回 double、部分版本返回 int：显式转换两边都能编译
		return (double) Minecraft.getInstance().getWindow().getGuiScale();
	}

	@Override
	public int textWidth(String value) {
		return this.font.width(value == null ? "" : value);
	}

	@Override
	public int button(int x, int y, int w, int h, String label, Runnable onPress) {
		Button widget = Button.builder(Component.literal(label == null ? "" : label),
				b -> onPress.run()).bounds(x, y, w, h).build();
		this.addRenderableWidget(widget);
		int id = this.nextButtonId++;
		this.widgets.put(id, widget);
		return id;
	}

	@Override
	public void buttonText(int id, String label) {
		AbstractWidget widget = this.widgets.get(id);
		if (widget != null) {
			widget.setMessage(Component.literal(label == null ? "" : label));
		}
	}

	@Override
	public void buttonEnabled(int id, boolean enabled) {
		AbstractWidget widget = this.widgets.get(id);
		if (widget != null) {
			widget.active = enabled;
		}
	}

	/** 按钮 / 滑块用原版的 active 字段置灰；输入框还要关掉编辑，否则仍能键入。 */
	@Override
	public void widgetEnabled(int id, boolean enabled) {
		AbstractWidget widget = this.widgets.get(id);
		if (widget == null) {
			return;
		}
		widget.active = enabled;
		if (widget instanceof EditBox box) {
			box.setEditable(enabled);
		}
	}

	@Override
	public int slider(int x, int y, int w, int h, String label, int value, int min, int max,
			IntConsumer onChange) {
		Slider widget = new Slider(x, y, w, h, label, value, min, max, onChange);
		this.addRenderableWidget(widget);
		int id = this.nextButtonId++;
		this.widgets.put(id, widget);
		this.sliders.put(id, widget);
		return id;
	}

	@Override
	public void sliderValue(int id, int value) {
		Slider widget = this.sliders.get(id);
		if (widget != null) {
			widget.setIntValue(value);
		}
	}

	@Override
	public void moveWidget(int id, int x, int y) {
		AbstractWidget widget = this.widgets.get(id);
		if (widget != null) {
			widget.setX(x);
			widget.setY(y);
		}
	}

	@Override
	public void widgetVisible(int id, boolean visible) {
		AbstractWidget widget = this.widgets.get(id);
		if (widget != null) {
			widget.visible = visible;
		}
	}

	@Override
	public int textBox(int x, int y, int w, int h, String hint, String initial,
			java.util.function.Consumer<String> onChange) {
		EditBox box = new EditBox(this.font, x, y, w, h, Component.literal(hint));
		box.setHint(Component.literal(hint));
		box.setValue(initial == null ? "" : initial);
		box.setResponder(onChange);
		this.addRenderableWidget(box);
		int id = this.nextButtonId++;
		this.widgets.put(id, box);
		return id;
	}

	@Override
	public String textBoxValue(int id) {
		AbstractWidget widget = this.widgets.get(id);
		return widget instanceof EditBox box ? box.getValue() : "";
	}

	@Override
	public boolean textBoxFocused(int id) {
		AbstractWidget widget = this.widgets.get(id);
		return widget instanceof EditBox box && box.isFocused();
	}

	@Override
	public void textBoxValue(int id, String value) {
		AbstractWidget widget = this.widgets.get(id);
		if (widget instanceof EditBox box) {
			box.setValue(value == null ? "" : value);
		}
	}

	@Override
	public void textBoxFocus(int id, boolean focused) {
		AbstractWidget widget = this.widgets.get(id);
		if (!(widget instanceof EditBox box)) {
			return;
		}
		box.setFocused(focused);
		// Screen 的焦点目标和 widget 自身的 focused 是两回事，两个都要改，
		// 否则键盘事件会被继续送给别的控件。
		this.setFocused(focused ? box : null);
	}

	@Override
	public int numberBox(int x, int y, int w, int h, String initial,
			java.util.function.Consumer<String> onChange, Runnable onCommit) {
		NumberBox box = new NumberBox(this.font, x, y, w, h, initial, onChange, onCommit);
		this.addRenderableWidget(box);
		int id = this.nextButtonId++;
		this.widgets.put(id, box);
		return id;
	}

	@Override
	public void textBoxColor(int id, int argb) {
		AbstractWidget widget = this.widgets.get(id);
		if (widget instanceof EditBox box) {
			box.setTextColor(argb);
		}
	}

	@Override
	public void clearButtons() {
		this.widgets.clear();
		this.sliders.clear();
		this.setFocused(null);
		this.clearWidgets();
	}

	@Override
	public void close() {
		this.onClose();
	}

	@Override
	public void open(Object screenHandle) {
		Ui.open(screenHandle);
	}

	@Override
	public String tr(String key, Object... args) {
		return Ui.tr(key, args);
	}

	// ------------------------------------------------------------------ Screen 生命周期

	@Override
	protected void init() {
		this.spec.init(this);
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		this.pendingTipLines = null;
		this.renderBackgroundCompat(graphics, mouseX, mouseY, partialTick);
		this.spec.render(new CanvasImpl(graphics, this.font), this, mouseX, mouseY, partialTick);
		super.render(graphics, mouseX, mouseY, partialTick);
		// tooltip 必须盖在原版 widget 之上，放在 super 之后画
		this.spec.renderOverlay(new CanvasImpl(graphics, this.font), this,
				mouseX, mouseY, partialTick);
		this.flushTooltip(graphics);
	}

	@Override
	public void tooltipLines(int mouseX, int mouseY, List<UiTooltip> lines) {
		if (lines == null || lines.isEmpty()) {
			return;
		}
		this.pendingTipLines = lines;
		this.pendingTipX = mouseX;
		this.pendingTipY = mouseY;
	}

	/**
	 * 交给原版绘制：跟随鼠标、自动换行、自动翻面。
	 *
	 * <p>1.21.8 起 {@code GuiGraphics} 把 tooltip 改成「下一帧再画」
	 * （{@code setTooltipForNextFrame}），旧的 {@code renderComponentTooltip}
	 * 已被移除；这里按版本各调各的。</p>
	 */
	private void flushTooltip(GuiGraphics graphics) {
		if (this.pendingTipLines == null) {
			return;
		}
		List<Component> components = new ArrayList<>(this.pendingTipLines.size());
		for (UiTooltip line : this.pendingTipLines) {
			// 每行各自上色：argb 为 0 的行跟随原版默认色
			Style style = line.argb == 0
					? Style.EMPTY
					: Style.EMPTY.withColor(line.argb & 0xFFFFFF);
			components.add(Component.literal(line.text).withStyle(style));
		}
		//? if >=1.21.8 {
		/*graphics.setComponentTooltipForNextFrame(this.font, components,
				this.pendingTipX, this.pendingTipY);
		*///?} else {
		graphics.renderComponentTooltip(this.font, components,
				this.pendingTipX, this.pendingTipY);
		//?}
		this.pendingTipLines = null;
	}

	@Override
	public void onClose() {
		Object parent = this.spec.parent();
		this.spec.close();
		Minecraft.getInstance().setScreen((Screen) parent);
	}

	@Override
	public boolean isPauseScreen() {
		return this.spec.pause();
	}

	// ------------------------------------------------------------------ 原版签名转发

	/*
	 * 所有输入事件都先交给原版控件，再由使用方处理。
	 *
	 * 顺序不能反：配置界面的按钮、滑块全是真实 widget，只有 super 会去遍历
	 * children 并把事件派发下去；早先直接 return spec.xxx 时，这些控件看着在那儿
	 * 却点不动、滑条也拖不快。
	 */

	private final void renderBackgroundCompat(GuiGraphics graphics, int mouseX, int mouseY,
			float partialTick) {
		//? if >=1.20.2 {
		/*this.renderBackground(graphics, mouseX, mouseY, partialTick);
		*///?} else {
		this.renderBackground(graphics);
		//?}
	}

	//? if >=1.21.9 {
	/*@Override
	public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
		if (this.spec.beforeKey(event.key())) {
			return true;
		}
		if (super.keyPressed(event)) {
			return true;
		}
		return this.spec.key(event.key(), event.scancode(), event.modifiers());
	}

	@Override
	public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick)) {
			return true;
		}
		return this.spec.click(event.x(), event.y(), event.button());
	}

	@Override
	public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event, double dragX, double dragY) {
		if (super.mouseDragged(event, dragX, dragY)) {
			return true;
		}
		return this.spec.drag(event.x(), event.y(), event.button());
	}

	@Override
	public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {
		if (super.mouseReleased(event)) {
			return true;
		}
		return this.spec.release(event.x(), event.y(), event.button());
	}
	*///?} else {
	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (this.spec.beforeKey(keyCode)) {
			return true;
		}
		if (super.keyPressed(keyCode, scanCode, modifiers)) {
			return true;
		}
		return this.spec.key(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (super.mouseClicked(mouseX, mouseY, button)) {
			return true;
		}
		return this.spec.click(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
		if (super.mouseDragged(mouseX, mouseY, button, dragX, dragY)) {
			return true;
		}
		return this.spec.drag(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		if (super.mouseReleased(mouseX, mouseY, button)) {
			return true;
		}
		return this.spec.release(mouseX, mouseY, button);
	}
	//?}

	//? if >=1.20.2 {
	/*@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		return this.spec.scroll(mouseX, mouseY, scrollY);
	}
	*///?} else {
	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
		return this.spec.scroll(mouseX, mouseY, delta);
	}
	//?}

	// ------------------------------------------------------------------ 数字输入框

	/**
	 * 整数输入框：滑条行的「切到数字输入」用。
	 *
	 * <p>只覆写回车提交；Esc 由 {@link UiScreen#beforeKey(int)} 在 Screen 关界面之前
	 * 拦下。1.21.11 起 EditBox 的键盘回调换成 {@code KeyEvent}，其余节点是
	 * {@code (int,int,int)} —— 这个分叉只存在于本文件，界面层看不到。</p>
	 */
	private static final class NumberBox extends EditBox {

		private static final int ENTER = 257;
		private static final int NUMPAD_ENTER = 335;

		private final Runnable onCommit;
		private boolean committing;

		NumberBox(Font font, int x, int y, int w, int h, String initial,
				java.util.function.Consumer<String> onChange, Runnable onCommit) {
			super(font, x, y, w, h, Component.literal(""));
			this.setMaxLength(24);
			this.setValue(initial == null ? "" : initial);
			// 不做字符过滤：非法内容必须能敲进去，界面层才能实时标红并说明原因。
			// responder 要挂在 setValue 之后，否则构造时的初始值会被当成一次用户输入。
			this.setResponder(onChange == null ? (java.util.function.Consumer<String>) ignored -> { } : onChange);
			this.onCommit = onCommit;
		}

		//? if >=1.21.11 {
		/*@Override
		public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
			if (event.key() == ENTER || event.key() == NUMPAD_ENTER) {
				this.commit();
				return true;
			}
			return super.keyPressed(event);
		}
		*///?} else {
		@Override
		public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
			if (keyCode == ENTER || keyCode == NUMPAD_ENTER) {
				this.commit();
				return true;
			}
			return super.keyPressed(keyCode, scanCode, modifiers);
		}
		//?}

		private void commit() {
			if (this.committing) {
				return;
			}
			this.committing = true;
			try {
				if (this.onCommit != null) {
					this.onCommit.run();
				}
			} finally {
				this.committing = false;
			}
		}
	}

	// ------------------------------------------------------------------ 原版滑块

	/**
	 * 整数滑块：把 {@link AbstractSliderButton} 的 0~1 比例量换算成整数区间。
	 *
	 * <p>显示文字就是当前数值 —— 这和 masa 系把值画在滑条里是同一个位置，
	 * 但用的是原版贴图与高亮状态。</p>
	 */
	private static final class Slider extends AbstractSliderButton {

		private final int min;
		private final int max;
		private final IntConsumer onChange;
		/** 外部设值时置位，避免把「刷新显示」当成一次用户拖动。 */
		private boolean silent;

		Slider(int x, int y, int width, int height, String label, int value, int min, int max,
				IntConsumer onChange) {
			super(x, y, width, height, Component.literal(label == null ? "" : label),
					ratio(value, min, max));
			this.min = min;
			this.max = max;
			this.onChange = onChange;
			this.updateMessage();
		}

		private static double ratio(int value, int min, int max) {
			return max <= min ? 0.0 : (double) (value - min) / (double) (max - min);
		}

		int intValue() {
			return this.min + (int) Math.round(this.value * (double) (this.max - this.min));
		}

		/** 外部改值：只刷新显示，不回调。 */
		void setIntValue(int value) {
			this.silent = true;
			this.value = ratio(value, this.min, this.max);
			this.silent = false;
			this.updateMessage();
		}

		@Override
		protected void updateMessage() {
			this.setMessage(Component.literal(String.valueOf(this.intValue())));
		}

		@Override
		protected void applyValue() {
			if (this.silent || this.onChange == null) {
				return;
			}
			this.onChange.accept(this.intValue());
		}
	}

	// ------------------------------------------------------------------ UiCanvas

	/** 把 {@code GuiGraphics} + {@code Font} 包装成纯 Java 绘图接口。 */
	static final class CanvasImpl implements UiCanvas {

		private final GuiGraphics graphics;
		private final Font font;

		CanvasImpl(GuiGraphics graphics, Font font) {
			this.graphics = graphics;
			this.font = font;
		}

		@Override
		public void fill(int x1, int y1, int x2, int y2, int argb) {
			this.graphics.fill(x1, y1, x2, y2, argb);
		}

		@Override
		public void text(String value, int x, int y, int argb, boolean shadow) {
			this.graphics.drawString(this.font, value, x, y, argb, shadow);
		}

		@Override
		public void centered(String value, int x, int y, int argb) {
			this.graphics.drawCenteredString(this.font, value, x, y, argb);
		}

		@Override
		public void scissor(int x1, int y1, int x2, int y2) {
			this.graphics.enableScissor(x1, y1, x2, y2);
		}

		@Override
		public void endScissor() {
			this.graphics.disableScissor();
		}

		@Override
		public int textWidth(String value) {
			return this.font.width(value);
		}

		@Override
		public String fit(String value, int maxWidth) {
			if (maxWidth <= 0 || this.font.width(value) <= maxWidth) {
				return value;
			}
			return this.font.plainSubstrByWidth(value, Math.max(8, maxWidth - 6)) + "…";
		}

		@Override
		public List<String> wrap(String value, int maxWidth) {
			List<String> result = new ArrayList<>();
			for (FormattedCharSequence line : this.font.split(Component.literal(value), maxWidth)) {
				StringBuilder sb = new StringBuilder();
				line.accept((index, style, codePoint) -> {
					sb.appendCodePoint(codePoint);
					return true;
				});
				result.add(sb.toString());
			}
			return result;
		}
	}
}
