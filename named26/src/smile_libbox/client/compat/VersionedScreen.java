package smile_libbox.client.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
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
 * 26.2（named 命名空间 / retained-mode GUI）的界面宿主。
 *
 * <p>这一代 {@code Screen} 不再有 {@code render(GuiGraphics,...)}，而是
 * {@code extractRenderState(GuiGraphicsExtractor,...)}；绘图方法也从
 * {@code drawString/drawCenteredString} 换成 {@code text/centeredText}。
 * 差异全部收在这里，使用方仍只实现 {@link UiScreen}。</p>
 */
public final class VersionedScreen extends Screen implements UiHost {

	private final UiScreen spec;
	private final Map<Integer, AbstractWidget> widgets = new HashMap<>();
	private final Map<Integer, Slider> sliders = new HashMap<>();
	private int nextId = 1;

	/** 本帧要显示的原版 tooltip；由 {@link #tooltip} 记下，帧末统一画。 */
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
		int id = this.nextId++;
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
		int id = this.nextId++;
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
		int id = this.nextId++;
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
		this.setFocused(focused ? box : null);
	}

	@Override
	public int numberBox(int x, int y, int w, int h, String initial,
			java.util.function.Consumer<String> onChange, Runnable onCommit) {
		NumberBox box = new NumberBox(this.font, x, y, w, h, initial, onChange, onCommit);
		this.addRenderableWidget(box);
		int id = this.nextId++;
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
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
			float partialTick) {
		this.pendingTipLines = null;
		this.spec.render(new CanvasImpl(graphics, this.font), this, mouseX, mouseY, partialTick);
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
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
	 * 26.2 的 {@code GuiGraphicsExtractor} 只有 {@code setTooltipForNextFrame} 系：
	 * tooltip 由这一帧末尾统一绘制，锚点是这里传入的鼠标坐标。
	 */
	private void flushTooltip(GuiGraphicsExtractor graphics) {
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
		graphics.setComponentTooltipForNextFrame(this.font, components,
				this.pendingTipX, this.pendingTipY);
		this.pendingTipLines = null;
	}

	@Override
	public void onClose() {
		this.spec.close();
		Ui.open(this.spec.parent());
	}

	@Override
	public boolean isPauseScreen() {
		return this.spec.pause();
	}

	// ------------------------------------------------------------------ 输入转发

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (this.spec.beforeKey(event.key())) {
			return true;
		}
		if (super.keyPressed(event)) {
			return true;
		}
		return this.spec.key(event.key(), event.scancode(), event.modifiers());
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick)) {
			return true;
		}
		return this.spec.click(event.x(), event.y(), event.button());
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		if (super.mouseDragged(event, dragX, dragY)) {
			return true;
		}
		return this.spec.drag(event.x(), event.y(), event.button());
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (super.mouseReleased(event)) {
			return true;
		}
		return this.spec.release(event.x(), event.y(), event.button());
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
			return true;
		}
		return this.spec.scroll(mouseX, mouseY, scrollY);
	}

	// ------------------------------------------------------------------ 数字输入框

	/** 26.2 的 EditBox 没有 setFilter，合法性交给界面层在提交时兜底。 */
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
			// 26.2 本来就没有 setFilter；responder 仍挂在 setValue 之后，
			// 避免构造时的初始值被当成一次用户输入。
			this.setResponder(onChange == null ? (java.util.function.Consumer<String>) ignored -> { } : onChange);
			this.onCommit = onCommit;
		}

		@Override
		public boolean keyPressed(KeyEvent event) {
			if (event.key() == ENTER || event.key() == NUMPAD_ENTER) {
				this.commit();
				return true;
			}
			return super.keyPressed(event);
		}

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

	private static final class Slider extends AbstractSliderButton {

		private final int min;
		private final int max;
		private final IntConsumer onChange;
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

	static final class CanvasImpl implements UiCanvas {

		private final GuiGraphicsExtractor graphics;
		private final Font font;

		CanvasImpl(GuiGraphicsExtractor graphics, Font font) {
			this.graphics = graphics;
			this.font = font;
		}

		@Override
		public void fill(int x1, int y1, int x2, int y2, int argb) {
			this.graphics.fill(x1, y1, x2, y2, argb);
		}

		@Override
		public void text(String value, int x, int y, int argb, boolean shadow) {
			this.graphics.text(this.font, value, x, y, argb, shadow);
		}

		@Override
		public void centered(String value, int x, int y, int argb) {
			this.graphics.centeredText(this.font, value, x, y, argb);
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

		/** 按像素宽度折行：只依赖 {@code Font.width(String)}，绕开 FormattedCharSequence 的版本差异。 */
		@Override
		public List<String> wrap(String value, int maxWidth) {
			List<String> lines = new ArrayList<>();
			if (maxWidth <= 0) {
				lines.add(value);
				return lines;
			}
			StringBuilder current = new StringBuilder();
			for (int i = 0; i < value.length(); ) {
				int codePoint = value.codePointAt(i);
				String ch = new String(Character.toChars(codePoint));
				if (current.length() > 0 && this.font.width(current + ch) > maxWidth) {
					lines.add(current.toString());
					current.setLength(0);
				}
				current.append(ch);
				i += Character.charCount(codePoint);
			}
			if (current.length() > 0 || lines.isEmpty()) {
				lines.add(current.toString());
			}
			return lines;
		}
	}
}
