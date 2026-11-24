package smile_libbox.api.ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 预设配置界面：masa 系排版 + 原版材质控件。
 *
 * <p>页面顶部是一排页签按钮（当前页置灰），正文是该页的配置项列表，
 * 每行是「标签列 + 控件 + 重置键」，滑块行还带一颗「切到数字输入框」的小按钮；
 * 标了 {@code boxOnly()} 的数值行不画滑条与那颗小按钮，控件列整条给输入框，
 * 内容在回车 / 关界面时写回配置；字符串行用原版 {@code EditBox} 直接键入。重置键与右下角的「完成 / 全部重置」都可以
 * 由调用方关掉（关掉重置后控件列一直延伸到右端；系统按钮全关、底栏又没放东西时整条底栏
 * 不占高度）；最底下那一行也可以由模组放自己的按钮与状态文字（{@code page.bottom}），
 * 不额外占高度。</p>
 *
 * <p><b>为什么不自己画：</b>按钮、滑块、输入框、滚动条滑块全部由 {@link UiHost} 创建成
 * 真正的原版 widget，贴图、悬停高亮、禁用态、讲述人全由游戏负责。这既是「和原版一致」
 * 的唯一做法，也顺手解决了手绘时那个贴图条带只有 200 px、控件一宽就画不出背景的问题。</p>
 *
 * <p><b>行高与滚动：</b>行高不是写死的 22，而是「可视区高度 ÷ 完整可见行数」——
 * 行数向上取整（比旧的 floor 多显示一行），并受控件高度 20 的下限约束。滚动按整行吸附，
 * 因此任何时刻列表里都只有完整的行，不会出现「标签画了半截、控件被藏起来」的情况；
 * 两页共用同一套行高与可见行数，切页时表格不跳。</p>
 *
 * <p>本类位于 {@code api} 包、只依赖 Java 原生类型，两个命名空间共用同一份字节码。</p>
 */
public final class ConfigScreen implements UiScreen {

	// ------------------------------------------------------------------ 布局常量

	private static final int TITLE_Y = 8;

	/** 页签栏。 */
	private static final int TAB_TOP = 26;
	private static final int TAB_H = 20;
	private static final int TAB_PADDING = 14;
	private static final int TAB_GAP = 4;

	/** 列表。 */
	private static final int LIST_X = 10;
	private static final int LIST_Y = 52;
	private static final int WIDGET_H = 20;
	/** 行高的名义值：只用来算「这一屏能放几行」，真正用的是 {@link #rowHeight}。 */
	private static final int ROW_H = 22;
	private static final int TEXT_LINE_H = 11;

	/**
	 * 控件宽度上限 206 = 贴图条带 200 + 端帽余量 6。
	 *
	 * <p>原版按钮走的是九宫格拉伸，其实不受这条约束；保留它是因为超过 206 之后
	 * 整行会被拉得很长，在宽屏上滑条又细又长反而不像 masa 系界面。</p>
	 *
	 * <p>调用方用 {@code showReset(false)} 关掉重置键时，上限会加上重置键让出来的那段
	 * —— 省下的横向空间归控件，而不是变成一片空白。</p>
	 */
	private static final int MAX_CONFIG_WIDTH = 206;
	private static final int MIN_CONFIG_WIDTH = 120;
	private static final int LABEL_GAP = 10;
	private static final int RESET_GAP = 2;
	private static final int RESET_PADDING = 10;

	/** 右端留白：{@code 24 × 3 ÷ GUI 缩放}，再限幅。 */
	private static final int RIGHT_MARGIN_REFERENCE = 24;
	private static final int RIGHT_MARGIN_SCALE_REFERENCE = 3;
	private static final int MIN_RIGHT_MARGIN = 8;
	private static final int MAX_RIGHT_MARGIN = 40;

	/** 列表右侧内缩与滚动条。 */
	private static final int LIST_RIGHT_INSET = 4;
	private static final int SCROLLBAR_WIDTH = 8;
	private static final int RESET_TO_SCROLLBAR_GAP = 6;
	private static final int MIN_THUMB_H = 16;

	/** 底部一行按钮。 */
	private static final int BOTTOM_H = 20;
	private static final int BOTTOM_GAP = 6;
	private static final int BOTTOM_PAD = 12;

	/**
	 * 滑块行右侧的「切换」小按钮：本身就是一颗原版 Button（带 widgets 贴图、悬停高亮、
	 * 禁用态），上面的图标先用字形占位，MaLiLib 那颗按钮用的是一张小图标贴图。
	 *
	 * <p>要换成贴图时不用动界面层：在 {@code VersionedScreen} 里把这类按钮换成一个会
	 * blit 的 widget，再改这里两个 {@code buttonText} 用的常量即可。</p>
	 *
	 * <p>{@code "T"} = 现在显示滑条，点它切到数字输入框；
	 * {@code "≡"} = 现在显示数字输入框，点它切回滑条。</p>
	 */
	private static final int SWITCH_W = 18;
	private static final int SWITCH_GAP = 2;
	private static final String SWITCH_TO_NUMBER = "T";
	private static final String SWITCH_TO_SLIDER = "≡";

	private static final int KEY_ESCAPE = 256;

	/** 输入框（数字框 / 字符串框）：合法 / 非法文字颜色。tooltip 交给原版绘制，这里不再自带配色。 */
	private static final int NUMBER_TEXT_OK = 0xFFE0E0E0;
	private static final int NUMBER_TEXT_BAD = 0xFFFF5555;

	private static final String RESET_KEY = "smile_libbox.gui.reset";
	private static final String RESET_ALL_KEY = "smile_libbox.gui.reset_all";
	private static final String DONE_KEY = "smile_libbox.gui.done";

	// ------------------------------------------------------------------ 状态

	private final ConfigSpec spec;

	private UiHost host;
	private int pageIndex;
	/** 列表向上卷起的「整行数」，不是像素。 */
	private int scrollRow;

	/** 本帧排版结果。 */
	private int labelWidth;
	private int configWidth;
	private int sliderWidth;
	private int resetWidth;
	private int rightEdge;
	private int listTop;
	private int listBottom;
	/** 可视区能放下的完整行数（两页一致）。 */
	private int visibleRows;
	/** 实际行高 = 可视区高度 ÷ 可见行数；上下留白 = (rowHeight − WIDGET_H) ÷ 2。 */
	private int rowHeight;
	/** 底部那一行（与「全部重置」「完成」同一行）的上边缘。 */
	private int bottomTop;
	/** 底栏这一帧要不要占高度：有系统按钮或模组自建项时为真。 */
	private boolean hasBottomBar = true;

	private final List<Slot> slots = new ArrayList<>();
	private final List<Integer> footerIds = new ArrayList<>();
	private final List<ConfigSpec.Footer> footerRefs = new ArrayList<>();
	/** 底部那一行里已经摆好的一项（含最终矩形，悬停与 tooltip 都用它）。 */
	private final List<Placed> bottomPlaced = new ArrayList<>();
	/** 底部按钮上一次的文字 / 禁用态，只在真的变了才写控件。 */
	private final Map<Integer, String> buttonLabelCache = new HashMap<>();
	private final Map<Integer, Boolean> buttonEnabledCache = new HashMap<>();
	private int doneId = -1;
	private int resetAllId = -1;
	private int scrollbarId = -1;
	private int scrollbarX;
	private int thumbHeight;
	private boolean dragging;

	/** 当前处于数字输入模式的行；同时最多一个。 */
	private Slot activeNumber;
	/** 重建（改窗口大小 / 切页）时暂存输入状态，重建完按行恢复，绝不提交或切回滑条。 */
	private ConfigSpec.Row pendingNumberRow;
	private String pendingNumberText;
	/** 「只要输入框」的行：重建时把各行的当前文本按行记下来，重建完原样填回。 */
	private final Map<ConfigSpec.Row, String> pendingBoxText = new HashMap<>();

	private int builtWidth = -1;
	private int builtHeight = -1;

	public ConfigScreen(ConfigSpec spec, Object parent) {
		this.spec = spec;
	}

	@Override
	public String title() {
		return this.spec.title;
	}

	@Override
	public Object parent() {
		return this.spec.parent;
	}

	@Override
	public boolean pause() {
		return false;
	}

	// ------------------------------------------------------------------ 生命周期

	@Override
	public void init(UiHost h) {
		this.host = h;
		this.rebuild();
	}

	private ConfigSpec.Page page() {
		return this.spec.pages.isEmpty() ? null : this.spec.pages.get(this.pageIndex);
	}

	/** 整屏重建：清空全部控件，按当前尺寸重新排版。切页 / 改窗口大小都走这里。 */
	private void rebuild() {
		if (this.host == null) {
			return;
		}
		// 重建会销毁所有 widget（包括输入框），先把输入内容存下来。
		// 注意：这里不提交、不切回滑条 —— 点别处 / 切页 / 改窗口大小都保持数字输入模式。
		Slot active = this.activeNumber;
		if (active != null) {
			this.pendingNumberRow = active.row;
			this.pendingNumberText = this.host.textBoxValue(active.numberId);
		}
		// 「只要输入框」的行同时可能有多行处在编辑态，逐行把文本存下来
		for (Slot slot : this.slots) {
			if (slot.boxOnly) {
				this.pendingBoxText.put(slot.row, this.host.textBoxValue(slot.numberId));
			}
		}
		this.activeNumber = null;
		this.dragging = false;
		this.builtWidth = this.host.width();
		this.builtHeight = this.host.height();

		this.host.clearButtons();
		this.slots.clear();
		this.footerIds.clear();
		this.footerRefs.clear();
		this.bottomPlaced.clear();
		this.buttonLabelCache.clear();
		this.buttonEnabledCache.clear();
		this.doneId = -1;
		this.resetAllId = -1;
		this.scrollbarId = -1;

		this.measure();
		this.createTabs();       // 页签折行会改 listTop，行高必须在它之后算
		this.computeGrid();
		this.createRows();
		this.createFooters();
		this.createScrollbar();
		this.clampScroll();
		this.layoutRows();
		this.refreshAvailability();
		this.restorePendingNumber();
		this.restoreBoxText();
	}

	/** 重建后把各「只要输入框」的行填回原来的文本（含还没提交的非法内容，标红照旧）。 */
	private void restoreBoxText() {
		if (this.pendingBoxText.isEmpty()) {
			return;
		}
		for (Slot slot : this.slots) {
			String text = this.pendingBoxText.remove(slot.row);
			if (!slot.boxOnly || text == null) {
				continue;
			}
			this.host.textBoxValue(slot.numberId, text);
			this.onNumberChanged(slot, text);
		}
		// 别的页上还没提交的文本留在缓存里，切回去时照样填回，不静默丢掉
	}

	/** 「只要输入框」的行统一提交：合法写回配置，非法保持红字（文本不还原）。 */
	private void commitBoxRows() {
		for (Slot slot : this.slots) {
			if (slot.boxOnly) {
				this.commitNumber(slot);
			}
		}
	}

	/** 重建后把输入状态挂回同一行；切页导致该行不在本页时，只落地合法值。 */
	private void restorePendingNumber() {
		ConfigSpec.Row row = this.pendingNumberRow;
		String text = this.pendingNumberText;
		this.pendingNumberRow = null;
		this.pendingNumberText = null;
		if (row == null) {
			return;
		}
		for (Slot slot : this.slots) {
			if (slot.row == row) {
				this.beginNumber(slot, text);
				return;
			}
		}
		if (row.numberError(text) == null) {
			this.slide(row, null, Integer.parseInt(text.trim()));
		}
	}

	/** 算出标签列、控件宽、重置键宽与右端锚点。 */
	private void measure() {
		int w = this.host.width();
		int h = this.host.height();

		double scale = Math.max(1.0, this.host.guiScale());
		int margin = (int) Math.round(RIGHT_MARGIN_REFERENCE * RIGHT_MARGIN_SCALE_REFERENCE / scale);
		margin = Math.max(MIN_RIGHT_MARGIN, Math.min(MAX_RIGHT_MARGIN, margin));
		// 下限必须让开滚动条，否则缩放变大时 RESET 会压上去
		margin = Math.max(margin, LIST_RIGHT_INSET + SCROLLBAR_WIDTH + RESET_TO_SCROLLBAR_GAP);

		this.rightEdge = w - margin;
		this.resetWidth = this.host.textWidth(Ui.tr(RESET_KEY)) + RESET_PADDING;

		// 不要重置键时，控件列一直顶到右端：滑条变长、切换键跟着右移，
		// 不会在右边留一块没用的空白（旧版是 CONTROL + 缺口 + RESET 三段式）。
		int controlRight = this.spec.showReset
				? this.rightEdge - this.resetWidth - RESET_GAP : this.rightEdge;
		int natural = this.naturalLabelWidth();
		int room = controlRight - (LIST_X + natural + LABEL_GAP);

		// 解除封锁的那段宽度正好是重置键原来的位置，控件「长」过去即是延长
		int maxWidth = MAX_CONFIG_WIDTH + (this.spec.showReset ? 0 : this.resetWidth + RESET_GAP);
		this.configWidth = Math.max(MIN_CONFIG_WIDTH, Math.min(maxWidth, room));
		// 控件宽被封顶后，剩下的横向空间交给标签列吸收：整行仍然收在右端锚点上，
		// 控件与右端之间不会留洞，两页也不会一宽一窄。
		this.labelWidth = Math.max(natural, controlRight - this.configWidth - LABEL_GAP - LIST_X);
		this.sliderWidth = Math.max(40, this.configWidth - SWITCH_W - SWITCH_GAP);

		this.listTop = LIST_Y;
		// 底部只有一行：系统按钮与模组自建按钮共用它，列表到它上面为止。
		// 系统按钮全关、模组也没往 bottom 放东西时，这条底栏不占高度（列表多显示一行）。
		this.hasBottomBar = this.spec.hasSystemButtons() || this.hasBottomItems();
		this.bottomTop = this.hasBottomBar ? h - BOTTOM_PAD - BOTTOM_H : h;
		this.listBottom = this.hasBottomBar ? this.bottomTop - 8 : h - BOTTOM_PAD;
		this.scrollbarX = w - LIST_RIGHT_INSET - SCROLLBAR_WIDTH;
	}

	/** 有没有哪一页往底栏放了东西（老式 footer 或 {@code bottom} 项）。有就保留那一行的高度，切页时不跳。 */
	private boolean hasBottomItems() {
		for (ConfigSpec.Page page : this.spec.pages) {
			if (!page.bottom.items.isEmpty() || !page.footers.isEmpty()) {
				return true;
			}
		}
		return false;
	}

	/** 两页共用的标签列宽：所有页里最宽的那条标签。 */
	private int naturalLabelWidth() {
		int width = 0;
		for (ConfigSpec.Page page : this.spec.pages) {
			for (ConfigSpec.Row row : page.rows) {
				if (row.kind == ConfigSpec.Kind.TEXT || row.label == null) {
					continue;
				}
				width = Math.max(width, this.host.textWidth(row.label));
			}
		}
		return width;
	}

	/**
	 * 行高 = 可视区高度 ÷ 完整可见行数。
	 *
	 * <p>行数按 {@code ceil(view / ROW_H)} 取（比旧的 floor 多出一行），但上限是
	 * {@code floor(view / WIDGET_H)}，保证每行至少放得下 20px 的原版控件。
	 * 例如 427×240 / 缩放 3：view=148 → 7 行 × 21px，正好铺满，不再有半截行。</p>
	 */
	private void computeGrid() {
		int view = this.viewHeight();
		if (view < WIDGET_H) {
			this.visibleRows = 1;
			this.rowHeight = Math.max(WIDGET_H, view);
			return;
		}
		int byWidget = view / WIDGET_H;
		int byNominal = (view + ROW_H - 1) / ROW_H;
		this.visibleRows = Math.max(1, Math.min(byWidget, byNominal));
		this.rowHeight = Math.max(WIDGET_H, view / this.visibleRows);
	}

	/** 页签：统一宽度，当前页置灰（masa 系就是靠这个表达「已经在这一页」）。 */
	private void createTabs() {
		int widest = 0;
		for (ConfigSpec.Page page : this.spec.pages) {
			widest = Math.max(widest, this.host.textWidth(page.title));
		}
		int width = widest + TAB_PADDING;

		int x = LIST_X;
		int y = TAB_TOP;
		for (int i = 0; i < this.spec.pages.size(); i++) {
			int index = i;
			String label = this.spec.pages.get(i).title;

			if (x + width > this.host.width() - LIST_X) {
				x = LIST_X;
				y += TAB_H + 2;
			}
			int id = this.host.button(x, y, width, TAB_H, label, () -> this.switchPage(index));
			this.host.buttonEnabled(id, index != this.pageIndex);
			x += width + TAB_GAP;
		}

		// 页签可能折成两行：列表顶要从最低那行页签下面开始
		int tabsBottom = y + TAB_H + 6;
		this.listTop = Math.max(LIST_Y, tabsBottom);
	}

	private void switchPage(int index) {
		if (index == this.pageIndex || index < 0 || index >= this.spec.pages.size()) {
			return;
		}
		// 离开这一页：只要输入框的行先落地合法值，非法内容留在缓存里，切回来还能接着改
		this.commitBoxRows();
		this.pageIndex = index;
		this.scrollRow = 0;
		this.rebuild();
	}

	/** 当前页每一行的主控件、切换键、数字输入框与重置键。 */
	private void createRows() {
		ConfigSpec.Page page = this.page();
		if (page == null) {
			return;
		}
		int controlX = LIST_X + this.labelWidth + LABEL_GAP;
		int resetX = this.rightEdge - this.resetWidth;

		for (ConfigSpec.Row row : page.rows) {
			Slot slot = new Slot(row);
			slot.height = this.rowHeight;
			switch (row.kind) {
				case TOGGLE: {
					slot.mainId = this.host.button(controlX, 0, this.configWidth, WIDGET_H,
							row.booleanValue ? Ui.tr("smile_libbox.gui.on") : Ui.tr("smile_libbox.gui.off"),
							() -> this.toggle(row, slot));
					break;
				}
				case SLIDER: {
					if (row.onlyBox()) {
						// 只要输入框：滑条与切换键都不建，控件列整条交给数字框。
						// numberMode 常开 = 输入框一直可见（没有「切回滑条」这一步）。
						slot.numberId = this.host.numberBox(controlX, 0, this.configWidth, WIDGET_H,
								String.valueOf(row.intValue),
								text -> this.onNumberChanged(slot, text),
								() -> this.commitNumber(slot));
						this.host.textBoxColor(slot.numberId, NUMBER_TEXT_OK);
						slot.numberMode = true;
						break;
					}
					slot.mainId = this.host.slider(controlX, 0, this.sliderWidth, WIDGET_H,
							String.valueOf(row.intValue), row.intValue, row.min, row.max,
							value -> this.slide(row, slot, value));
					slot.switchId = this.host.button(controlX + this.sliderWidth + SWITCH_GAP, 0,
							SWITCH_W, WIDGET_H, SWITCH_TO_NUMBER, () -> this.toggleNumber(slot));
					slot.numberId = this.host.numberBox(controlX, 0, this.sliderWidth, WIDGET_H,
							String.valueOf(row.intValue),
							text -> this.onNumberChanged(slot, text),
							() -> this.commitNumber(slot));
					this.host.textBoxColor(slot.numberId, NUMBER_TEXT_OK);
					this.host.widgetVisible(slot.numberId, false);
					break;
				}
				case ACTION: {
					slot.mainId = this.host.button(controlX, 0, this.configWidth, WIDGET_H,
							this.actionLabel(row), row.onClick == null ? () -> { } : row.onClick);
					break;
				}
				case STRING: {
					// 原版 EditBox：内容随便敲，合法性交给 row.stringError 判，
					// 不在这里过滤字符 —— 过滤掉就永远看不到「哪里不对」
					slot.mainId = this.host.textBox(controlX, 0, this.configWidth, WIDGET_H,
							"", row.stringValue, text -> this.onStringChanged(slot, text));
					this.refreshStringState(slot);
					break;
				}
				default:
					break;
			}
			if (this.spec.showReset && row.resettable()) {
				slot.resetId = this.host.button(resetX, 0, this.resetWidth, WIDGET_H,
						Ui.tr(RESET_KEY), () -> this.resetRow(row, slot));
				// 出生就是默认值的行，重置键一开始就该是灰的
				this.refreshResetState(row, slot);
			}
			this.slots.add(slot);
		}
	}

	private String actionLabel(ConfigSpec.Row row) {
		if (row.buttonLabel == null) {
			return "…";
		}
		String text = row.buttonLabel.get();
		return text == null ? "…" : text;
	}

	/**
	 * 底部只有一行：右端是系统按钮（完成 / 全部重置，都可关掉），左边留给模组的按钮。
	 *
	 * <p>系统按钮从右往左排，缺了哪一个都不留空位；模组按钮按各自的对齐方式两头堆，
	 * 宽度按文字算，撞上系统按钮时收窄。一颗系统按钮都没开、也没放自建项时，这一步
	 * 直接跳过 —— 这一行连同高度一起交给列表（{@code hasBottomBar} 在 {@code measure} 里定）。</p>
	 */
	private void createFooters() {
		if (!this.hasBottomBar) {
			return;
		}
		// 系统按钮与模组自建按钮共用这一行
		int y = this.bottomTop;

		int cursor = this.rightEdge;
		if (this.spec.showDone) {
			String label = this.spec.doneLabel == null || this.spec.doneLabel.isEmpty()
					? Ui.tr(DONE_KEY) : this.spec.doneLabel;
			int width = Math.max(60, this.host.textWidth(label) + 20);
			this.doneId = this.host.button(cursor - width, y, width, BOTTOM_H, label,
					this::pressDone);
			cursor -= width + BOTTOM_GAP;
		}
		if (this.spec.showReset) {
			int width = Math.max(50, this.host.textWidth(Ui.tr(RESET_ALL_KEY)) + 16);
			this.resetAllId = this.host.button(cursor - width, y, width, BOTTOM_H,
					Ui.tr(RESET_ALL_KEY), this::resetAll);
			cursor -= width + BOTTOM_GAP;
		}
		int systemLeft = cursor;

		ConfigSpec.Page page = this.page();
		if (page == null) {
			return;
		}

		// 老式页脚（库自己从左往右排，仍给旧代码留着）
		int x = LIST_X;
		for (ConfigSpec.Footer footer : page.footers) {
			int width = Math.max(40, this.host.textWidth(footer.label.get()) + 16);
			if (x + width > systemLeft) {
				break;
			}
			int id = this.host.button(x, y, width, BOTTOM_H, footer.label.get(), footer.onClick);
			if (footer.enabled != null) {
				this.host.buttonEnabled(id, Boolean.TRUE.equals(footer.enabled.get()));
			}
			this.footerIds.add(id);
			this.footerRefs.add(footer);
			x += width + BOTTOM_GAP;
		}

		this.createBottomItems(page, systemLeft);
		this.refreshBottom();
	}

	/**
	 * 底部那一行：宽度按文字算，横向由每一项的
	 * {@link ConfigSpec.Bottom.Align 对齐} 决定从哪头开始堆。
	 *
	 * <p>左对齐的按钮从左边界往右接，右对齐的从系统按钮左侧往左接（先加的在最右）。
	 * 同一行可以混着用，两边撞上时后加的收窄。只读文字排在按钮之后，按剩余宽度截断。</p>
	 */
	private void createBottomItems(ConfigSpec.Page page, int systemLeft) {
		// 右端边界：系统按钮之前；两颗都关掉时直接贴右端
		int limit = this.spec.hasSystemButtons()
				? Math.min(systemLeft - ConfigSpec.Bottom.GAP, this.rightEdge)
				: this.rightEdge;
		int leftEnd = LIST_X;
		int rightStart = limit;
		int textY = this.bottomTop + (BOTTOM_H - 8) / 2;
		List<ConfigSpec.Item> texts = new ArrayList<>();

		for (ConfigSpec.Item item : page.bottom.items) {
			if (item.kind == ConfigSpec.Bottom.Kind.TEXT) {
				texts.add(item);
				continue;
			}
			int width = Math.max(ConfigSpec.Bottom.MIN_BUTTON_WIDTH,
					this.host.textWidth(item.label()) + ConfigSpec.Bottom.BUTTON_PADDING);
			int bx;
			if (item.align == ConfigSpec.Bottom.Align.RIGHT) {
				bx = Math.max(leftEnd, rightStart - width);
				width = Math.max(20, rightStart - bx);
				rightStart = bx - ConfigSpec.Bottom.GAP;
			} else {
				bx = leftEnd;
				width = Math.max(20, Math.min(width, rightStart - bx));
				leftEnd = bx + width + ConfigSpec.Bottom.GAP;
			}
			int id = this.host.button(bx, this.bottomTop, width, BOTTOM_H,
					item.label(), item.onClick);
			this.host.buttonEnabled(id, item.enabled());
			this.bottomPlaced.add(new Placed(item, id, bx, this.bottomTop, width, BOTTOM_H));
		}

		// 文字项：按当前文字宽占位，排不下就靠 fit 截断（宽度记在 Placed.width 里）
		for (ConfigSpec.Item item : texts) {
			String text = item.label();
			int textWidth = text.isEmpty() ? 0 : this.host.textWidth(text);
			int tx;
			int available;
			if (item.align == ConfigSpec.Bottom.Align.RIGHT) {
				tx = Math.max(leftEnd, rightStart - textWidth);
				available = rightStart - tx;
				rightStart = tx - ConfigSpec.Bottom.GAP;
			} else {
				tx = leftEnd;
				available = Math.max(0, rightStart - leftEnd);
				leftEnd = tx + Math.min(textWidth, available) + ConfigSpec.Bottom.GAP;
			}
			this.bottomPlaced.add(new Placed(item, -1, tx, textY, available, 8));
		}
	}

	/** 「完成」的默认行为是关界面；模组可以用 {@code doneAction} 改掉。 */
	private void pressDone() {
		if (this.spec.doneAction != null) {
			this.spec.doneAction.run();
			return;
		}
		this.host.close();
	}

	/**
	 * 每帧刷新底部按钮的文字与禁用态。
	 *
	 * <p>「有批量任务在跑 → 两个按钮一起置灰」「剩余 N 个」这类状态都写在
	 * {@code Supplier} 里，界面不用自己去找控件。</p>
	 */
	private void refreshBottom() {
		for (int i = 0; i < this.footerIds.size() && i < this.footerRefs.size(); i++) {
			ConfigSpec.Footer footer = this.footerRefs.get(i);
			int id = this.footerIds.get(i);
			this.applyButton(id, footer.label.get(),
					footer.enabled == null || Boolean.TRUE.equals(footer.enabled.get()));
		}
		for (Placed placed : this.bottomPlaced) {
			if (placed.id < 0) {
				continue;
			}
			this.applyButton(placed.id, placed.item.label(), placed.item.enabled());
		}
	}

	/** 只在值真的变了时才写控件，避免每帧重建按钮文字。 */
	private void applyButton(int id, String label, boolean enabled) {
		String text = label == null ? "" : label;
		if (!text.equals(this.buttonLabelCache.get(id))) {
			this.buttonLabelCache.put(id, text);
			this.host.buttonText(id, text);
		}
		Boolean last = this.buttonEnabledCache.get(id);
		if (last == null || last != enabled) {
			this.buttonEnabledCache.put(id, enabled);
			this.host.buttonEnabled(id, enabled);
		}
	}

	/** 底部那一行里的文字项：空串跳过，{@code argb == 0} 用默认浅灰，超宽按可用宽度截断。 */
	private void renderBottomText(UiCanvas c) {
		for (Placed placed : this.bottomPlaced) {
			if (placed.item.kind != ConfigSpec.Bottom.Kind.TEXT) {
				continue;
			}
			String text = placed.item.label();
			// 这一行已经被按钮占满时不画，免得压到系统按钮上
			if (text.isEmpty() || placed.width <= 0) {
				continue;
			}
			String shown = c.fit(text, placed.width);
			c.text(shown, placed.x, placed.y,
					placed.item.argb == 0 ? 0xFFE6E6E6 : placed.item.argb, false);
		}
	}

	/** 底部那一行里已经摆好的一项（含最终矩形，用于悬停判定与 tooltip）。 */
	private static final class Placed {
		final ConfigSpec.Item item;
		final int id;
		final int x;
		final int y;
		final int width;
		final int height;

		Placed(ConfigSpec.Item item, int id, int x, int y, int width, int height) {
			this.item = item;
			this.id = id;
			this.x = x;
			this.y = y;
			this.width = width;
			this.height = height;
		}
	}

	/**
	 * 滚动条做成「背景 + 前景」两层：背景是画布上的暗色轨道，前景是一颗真正的原版
	 * 按钮（自带 widgets 贴图与悬停态），拖动它就能滚。这样不用碰各版本差异极大的
	 * blitSprite 签名，也能拿到原版材质。
	 */
	private void createScrollbar() {
		int view = this.viewHeight();
		int rows = this.slots.size();
		int ratio = rows <= 0 ? view : (int) ((long) view * this.visibleRows / rows);
		this.thumbHeight = Math.max(MIN_THUMB_H, Math.min(view, ratio));
		this.scrollbarId = this.host.button(this.scrollbarX, this.listTop,
				SCROLLBAR_WIDTH, this.thumbHeight, "", () -> { });
		this.host.widgetVisible(this.scrollbarId, false);
	}

	// ------------------------------------------------------------------ 数字输入框

	private void toggleNumber(Slot slot) {
		if (slot.numberMode) {
			// 再点切换按钮一定能切回滑条：合法就落地，非法就丢弃这次输入
			if (!this.commitNumber(slot)) {
				this.cancelNumber(slot);
			}
			return;
		}
		if (this.activeNumber != null && this.activeNumber != slot) {
			// 另一行还开着：合法就提交，非法就地丢弃，避免同时出现两个输入框
			if (!this.commitNumber(this.activeNumber)) {
				this.cancelNumber(this.activeNumber);
			}
		}
		this.beginNumber(slot, String.valueOf(slot.row.intValue));
	}

	private void beginNumber(Slot slot, String text) {
		// 前置项关着的行不接受输入（控件已经置灰，这里再兜一层）
		if (!slot.row.available()) {
			return;
		}
		slot.numberMode = true;
		this.activeNumber = slot;
		this.host.textBoxValue(slot.numberId,
				text == null ? String.valueOf(slot.row.intValue) : text);
		this.host.textBoxFocus(slot.numberId, true);
		if (slot.switchId >= 0) {
			this.host.buttonText(slot.switchId, SWITCH_TO_SLIDER);
		}
		this.onNumberChanged(slot, this.host.textBoxValue(slot.numberId));
		this.applySlot(slot);
	}

	/** 每次按键都走这里：校验正则与 min~max，非法立刻标红（鼠标悬停时另给原因与范围）。 */
	private void onNumberChanged(Slot slot, String text) {
		if (!slot.numberMode) {
			return;
		}
		slot.numberError = slot.row.numberError(text);
		slot.numberValid = slot.numberError == null;
		this.host.textBoxColor(slot.numberId, slot.numberValid ? NUMBER_TEXT_OK : NUMBER_TEXT_BAD);
		if (slot.boxOnly) {
			// 只要输入框的行一直处在输入状态，这里只登记「正在改哪一行」，
			// 不写配置 —— 写回发生在回车 / 关界面。
			this.activeNumber = slot;
		}
	}

	/** 每次键入都走这里：存值、按正则校验、非法标红；只有合法内容写回配置。 */
	private void onStringChanged(Slot slot, String text) {
		ConfigSpec.Row row = slot.row;
		if (row.kind != ConfigSpec.Kind.STRING) {
			return;
		}
		String value = text == null ? "" : text;
		row.stringValue = value;
		slot.stringError = row.stringError(value);
		this.host.textBoxColor(slot.mainId,
				slot.stringError == null ? NUMBER_TEXT_OK : NUMBER_TEXT_BAD);
		if (slot.stringError == null && row.stringSave != null) {
			row.stringSave.accept(value);
		}
		this.refreshResetState(row, slot);
	}

	/** 按当前内容重新判定合法性并给输入框上色（建行、重置、切换前置项后调用）。 */
	private void refreshStringState(Slot slot) {
		if (slot.mainId < 0 || slot.row.kind != ConfigSpec.Kind.STRING) {
			return;
		}
		slot.stringError = slot.row.stringError(slot.row.stringValue);
		this.host.textBoxColor(slot.mainId,
				slot.stringError == null ? NUMBER_TEXT_OK : NUMBER_TEXT_BAD);
	}

	/**
	 * 前置项变化后统一刷新从属行：控件按原版规则置灰、正在编辑的输入框退出输入模式、
	 * 标签文字变暗、tooltip 换成「先开启前置项」。
	 *
	 * <p>一个前置可以带多个从属行，所以这里遍历全表而不是只处理刚被点的那一行 ——
	 * 关掉前置项时，它会连带关掉一串。</p>
	 */
	private void refreshAvailability() {
		for (Slot slot : this.slots) {
			boolean enabled = slot.row.available();
			slot.available = enabled;
			if (!enabled && slot.numberMode && !slot.boxOnly) {
				this.cancelNumber(slot);
			}
			if (slot.mainId >= 0) {
				this.host.widgetEnabled(slot.mainId, enabled);
			}
			if (slot.switchId >= 0) {
				this.host.widgetEnabled(slot.switchId, enabled);
			}
			if (slot.numberId >= 0) {
				this.host.widgetEnabled(slot.numberId, enabled);
			}
			this.refreshResetState(slot.row, slot);
		}
	}

	/**
	 * 回车 / 再点切换键 / 关界面时尝试提交：合法才写回配置并切回滑条。
	 *
	 * <p>非法输入<b>不提交</b>。回车时保持红字让用户就地改（要看原因与范围就把鼠标移到
	 * 输入框上）；再点切换键或关界面则由调用方 {@code cancelNumber} 丢弃这次输入。点界面
	 * 别的地方不会触发本方法 —— 输入模式只在回车、切换按钮或 Esc 时结束。</p>
	 *
	 * @return 是否成功提交；{@code false} 表示内容非法、仍停留在数字输入模式
	 */
	private boolean commitNumber(Slot slot) {
		if (slot == null || !slot.numberMode) {
			return true;
		}
		String raw = this.host.textBoxValue(slot.numberId);
		if (slot.row.numberError(raw) != null) {
			this.onNumberChanged(slot, raw);
			if (this.activeNumber == slot) {
				this.host.textBoxFocus(slot.numberId, true);
			}
			return false;
		}
		int parsed = Integer.parseInt(raw.trim());
		this.slide(slot.row, slot, parsed);
		if (slot.mainId >= 0) {
			this.host.sliderValue(slot.mainId, slot.row.intValue);
		}
		this.host.textBoxValue(slot.numberId, String.valueOf(slot.row.intValue));
		this.host.textBoxColor(slot.numberId, NUMBER_TEXT_OK);
		slot.numberValid = true;
		slot.numberError = null;
		if (slot.boxOnly) {
			// 只要输入框的行没有「切回滑条」这一步：提交后仍留在框里，可以接着改
			if (this.activeNumber == slot) {
				this.host.textBoxFocus(slot.numberId, true);
			}
			return true;
		}
		this.endNumber(slot);
		return true;
	}

	/** Esc：丢弃输入，值不变。 */
	private void cancelNumber(Slot slot) {
		if (slot == null || !slot.numberMode) {
			return;
		}
		this.host.textBoxValue(slot.numberId, String.valueOf(slot.row.intValue));
		if (slot.boxOnly) {
			// 输入框一直可见，不能「退出输入模式」，只把内容与颜色恢复成原值
			slot.numberValid = true;
			slot.numberError = null;
			this.host.textBoxColor(slot.numberId, NUMBER_TEXT_OK);
			return;
		}
		this.endNumber(slot);
	}

	private void endNumber(Slot slot) {
		slot.numberMode = false;
		slot.numberValid = true;
		slot.numberError = null;
		this.host.textBoxColor(slot.numberId, NUMBER_TEXT_OK);
		this.host.textBoxFocus(slot.numberId, false);
		if (slot.switchId >= 0) {
			this.host.buttonText(slot.switchId, SWITCH_TO_NUMBER);
		}
		if (this.activeNumber == slot) {
			this.activeNumber = null;
		}
		this.applySlot(slot);
	}

	// ------------------------------------------------------------------ 交互

	private void toggle(ConfigSpec.Row row, Slot slot) {
		row.booleanValue = !row.booleanValue;
		if (row.booleanSave != null) {
			row.booleanSave.accept(row.booleanValue);
		}
		if (slot.mainId >= 0) {
			this.host.buttonText(slot.mainId,
					row.booleanValue ? Ui.tr("smile_libbox.gui.on") : Ui.tr("smile_libbox.gui.off"));
		}
		this.refreshResetState(row, slot);
		// 前置项被拨动：所有指着它的从属行要跟着改可用状态
		this.refreshAvailability();
	}

	private void slide(ConfigSpec.Row row, Slot slot, int value) {
		int clamped = Math.max(row.min, Math.min(row.max, value));
		if (clamped == row.intValue) {
			return;
		}
		row.intValue = clamped;
		if (row.intSave != null) {
			row.intSave.accept(clamped);
		}
		this.refreshResetState(row, slot);
	}

	private void resetRow(ConfigSpec.Row row, Slot slot) {
		if (!row.resetToDefault()) {
			return;
		}
		this.syncValueWidgets(slot);
		this.refreshResetState(row, slot);
		this.refreshAvailability();
	}

	/** 已经是默认值、或前置项没开启，就把重置键置灰，和 masa 系的手感一致。 */
	private void refreshResetState(ConfigSpec.Row row, Slot slot) {
		if (slot != null && slot.resetId >= 0) {
			this.host.buttonEnabled(slot.resetId, row.available() && !row.isDefaultValue());
		}
	}

	private void syncValueWidgets(Slot slot) {
		if (slot.row.kind == ConfigSpec.Kind.TOGGLE) {
			if (slot.mainId >= 0) {
				this.host.buttonText(slot.mainId, slot.row.booleanValue
						? Ui.tr("smile_libbox.gui.on") : Ui.tr("smile_libbox.gui.off"));
			}
			return;
		}
		if (slot.row.kind == ConfigSpec.Kind.SLIDER) {
			if (slot.mainId >= 0) {
				this.host.sliderValue(slot.mainId, slot.row.intValue);
			}
			if (slot.numberId >= 0) {
				this.host.textBoxValue(slot.numberId, String.valueOf(slot.row.intValue));
			}
		}
		if (slot.row.kind == ConfigSpec.Kind.STRING && slot.mainId >= 0) {
			this.host.textBoxValue(slot.mainId, slot.row.stringValue);
			this.refreshStringState(slot);
		}
	}

	private void resetAll() {
		for (Slot slot : this.slots) {
			if (!slot.row.resettable()) {
				continue;
			}
			slot.row.resetToDefault();
			this.syncValueWidgets(slot);
			this.refreshResetState(slot.row, slot);
		}
		// 全部重置可能把前置项一起关掉，可用状态要按新值重算
		this.refreshAvailability();
	}

	// ------------------------------------------------------------------ 绘制

	@Override
	public void render(UiCanvas c, UiHost h, int mouseX, int mouseY, float delta) {
		// 窗口尺寸变化时重建：控件是真实 widget，位置要重新算
		if (h.width() != this.builtWidth || h.height() != this.builtHeight) {
			this.rebuild();
		}

		// 底部按钮的文字与禁用态每帧重取：「有批量任务在跑」时两个按钮立刻一起变灰
		this.refreshBottom();

		// 数字输入模式不因失焦而结束：点别处只是让 EditBox 失焦，输入框仍在，
		// 用户点回来可以继续改；只有回车 / 切换按钮 / Esc 会结束输入模式。
		c.centered(this.spec.title, h.width() / 2, TITLE_Y, 0xFFFFFFFF);

		ConfigSpec.Page page = this.page();
		if (page == null) {
			return;
		}

		c.scissor(LIST_X, this.listTop, h.width() - LIST_RIGHT_INSET, this.listBottom);
		for (Slot slot : this.slots) {
			if (!slot.visible) {
				continue;
			}
			this.drawLabel(c, slot);
		}
		c.endScissor();

		this.drawScrollbarTrack(c, h);
	}

	private void drawLabel(UiCanvas c, Slot slot) {
		ConfigSpec.Row row = slot.row;
		if (row.kind == ConfigSpec.Kind.TEXT) {
			int maxLines = Math.max(1, (slot.height - 2) / TEXT_LINE_H);
			int y = slot.y + 2;
			int line = 0;
			for (String text : c.wrap(row.label == null ? "" : row.label, this.availableLabelWidth())) {
				if (line++ >= maxLines) {
					break;
				}
				c.text(text, LIST_X, y, 0xFFB0B0B0, false);
				y += TEXT_LINE_H;
			}
			return;
		}
		int textY = slot.y + (slot.height - 8) / 2;
		String label = c.fit(row.label == null ? "" : row.label, this.labelWidth);
		// 从属项被禁用时标签跟着变暗，和控件一起表达「这一行现在不管用」
		c.text(label, LIST_X, textY, slot.available ? 0xFFE6E6E6 : 0xFF7F7F7F, false);
	}

	/** 说明文字可以占用到控件列的右端，不必受标签列限制。 */
	private int availableLabelWidth() {
		return Math.max(40, this.rightEdge - LIST_X);
	}

	/** 滚动条「背景」：暗色轨道 + 两侧 1px 亮边；「前景」是那颗原版按钮滑块。 */
	private void drawScrollbarTrack(UiCanvas c, UiHost h) {
		if (this.maxScrollRows() <= 0) {
			return;
		}
		int x = this.scrollbarX;
		c.fill(x, this.listTop, x + SCROLLBAR_WIDTH, this.listBottom, 0xFF0A0A0A);
		c.fill(x + 1, this.listTop, x + SCROLLBAR_WIDTH - 1, this.listBottom, 0xA0202020);
		c.fill(x, this.listTop, x + 1, this.listBottom, 0xFF3A3A3A);
		c.fill(x + SCROLLBAR_WIDTH - 1, this.listTop, x + SCROLLBAR_WIDTH,
				this.listBottom, 0xFF3A3A3A);
	}

	// ------------------------------------------------------------------ tooltip（全部交给原版）

	/**
	 * 两处提示都走原版 tooltip：{@link UiHost#tooltip} 由宿主调
	 * {@code renderComponentTooltip} / {@code setComponentTooltipForNextFrame}，
	 * 因此是原版的黑框、原版换行、原版贴图，并且**跟随鼠标**、自动翻到屏幕内的另一侧。
	 *
	 * <p>优先级：</p>
	 * <ol>
	 *   <li>鼠标停在被前置项禁用的行上：红字提示「需要先开启『xxx』」；</li>
	 *   <li>数字输入框<b>内容非法</b>且鼠标停在输入框上：红字显示原因 + 允许范围；</li>
	 *   <li>鼠标停在右侧切换键上：显示该行的范围提示（按键压在输入区外，不挡字）；</li>
	 *   <li>鼠标停在某一行的<b>标签文字区</b>：显示该行自带的 {@code tooltip} 字段。</li>
	 * </ol>
	 *
	 * <p><b>输入框本身合法时不提示。</b>tooltip 框正好盖在输入框上方，一显示就挡住
	 * 正在键入的文字；只有出错时才需要这段说明，所以合法内容保持静默。</p>
	 */
	@Override
	public void renderOverlay(UiCanvas c, UiHost h, int mouseX, int mouseY, float delta) {
		// 底部那一行的只读文字：画在原版控件之上，颜色由 mod 指定（红字状态提示）
		this.renderBottomText(c);

		// 灰着的自建按钮：悬停说明为什么点不了（没配就写「该选项暂不可用」）
		for (Placed placed : this.bottomPlaced) {
			if (placed.id < 0 || placed.item.enabled()) {
				continue;
			}
			if (inRect(mouseX, mouseY, placed.x, placed.y, placed.width, placed.height)) {
				h.tooltip(mouseX, mouseY, List.of(placed.item.disabledTipText()));
				return;
			}
		}

		Slot hovered = this.rowAt(mouseY);

		// 被前置项禁用的行：先给这一项本身的说明，最后一行红字告诉玩家去开前置项
		if (hovered != null && !hovered.available) {
			List<UiTooltip> tips = new ArrayList<>(2);
			if (hovered.row.tooltip != null && !hovered.row.tooltip.isEmpty()) {
				tips.add(UiTooltip.of(hovered.row.tooltip));
			}
			String parent = hovered.row.requires == null || hovered.row.requires.label == null
					? "" : hovered.row.requires.label;
			tips.add(UiTooltip.colored(
					Ui.tr("smile_libbox.gui.requires", parent), NUMBER_TEXT_BAD));
			h.tooltipLines(mouseX, mouseY, tips);
			return;
		}

		Slot slot = this.activeNumber;
		if (slot != null && slot.numberMode && slot.visible) {
			int controlX = LIST_X + this.labelWidth + LABEL_GAP;
			int widgetY = slot.y + (slot.height - WIDGET_H) / 2;
			int boxWidth = slot.boxOnly ? this.configWidth : this.sliderWidth;
			boolean overNumber = inRect(mouseX, mouseY, controlX, widgetY,
					boxWidth, WIDGET_H);
			// 输入框里：只在内容非法时提示，避免盖住正在输入的字；错误提示整条用红字
			if (overNumber && !slot.numberValid) {
				h.tooltip(mouseX, mouseY, this.numberTip(slot), NUMBER_TEXT_BAD);
				return;
			}
			boolean overSwitch = slot.switchId >= 0 && inRect(mouseX, mouseY,
					controlX + this.sliderWidth + SWITCH_GAP, widgetY, SWITCH_W, WIDGET_H);
			if (overSwitch) {
				h.tooltip(mouseX, mouseY, this.numberTip(slot));
				return;
			}
		}

		// 字符串输入框：内容不合法、且鼠标停在框上时才提示（合法时不弹，免得挡住正在键入的字）
		if (hovered != null && hovered.stringError != null
				&& this.overControl(mouseX, mouseY, hovered)) {
			h.tooltip(mouseX, mouseY,
					List.of(Ui.tr("smile_libbox.gui.number.bad_string", hovered.row.regexName())),
					NUMBER_TEXT_BAD);
			return;
		}

		if (hovered == null || hovered.row.tooltip == null || hovered.row.tooltip.isEmpty()) {
			return;
		}
		// 只认标签列：控件自己已经有原版的手感，不该再被说明文字糊住
		if (mouseX < LIST_X || mouseX > LIST_X + this.labelWidth) {
			return;
		}
		h.tooltip(mouseX, mouseY, List.of(hovered.row.tooltip));
	}

	private static boolean inRect(double x, double y, int left, int top, int width, int height) {
		return x >= left && x <= left + width && y >= top && y <= top + height;
	}

	/** 鼠标是否落在这一行的控件列上（滑块 / 输入框 / 切换键都在这条带子里）。 */
	private boolean overControl(double mouseX, double mouseY, Slot slot) {
		int controlX = LIST_X + this.labelWidth + LABEL_GAP;
		int widgetY = slot.y + (slot.height - WIDGET_H) / 2;
		return inRect(mouseX, mouseY, controlX, widgetY, this.configWidth, WIDGET_H);
	}

	/** 鼠标纵坐标命中的可见行。整行吸附滚动保证行与行不重叠，命中一个就返回。 */
	private Slot rowAt(double mouseY) {
		for (Slot slot : this.slots) {
			if (slot.visible && mouseY >= slot.y && mouseY < slot.y + slot.height) {
				return slot;
			}
		}
		return null;
	}

	/**
	 * 数字输入框的提示内容。
	 *
	 * <p>非法时只有一行，按两类模板出：</p>
	 * <ul>
	 *   <li>内容不是正则能接受的 →「不合法的字符串！仅支持{正则名}」；</li>
	 *   <li>是数字但越界 →「不合法的数字！仅支持{min}~{max}之间的数据」。</li>
	 * </ul>
	 *
	 * <p>合法时（只在悬停切换键时才会走到）给范围与操作提示。</p>
	 */
	private List<String> numberTip(Slot slot) {
		ConfigSpec.Row row = slot.row;
		if (!slot.numberValid) {
			if ("range_error".equals(slot.numberError)) {
				return List.of(Ui.tr("smile_libbox.gui.number.bad_range",
						String.valueOf(row.min), String.valueOf(row.max)));
			}
			return List.of(Ui.tr("smile_libbox.gui.number.bad_string", row.regexName()));
		}
		List<String> lines = new ArrayList<>(2);
		lines.add(Ui.tr("smile_libbox.gui.number.range") + row.min + " ~ " + row.max);
		lines.add(Ui.tr("smile_libbox.gui.number.tip"));
		return lines;
	}

	// ------------------------------------------------------------------ 排版与滚动

	private int viewHeight() {
		return this.listBottom - this.listTop;
	}

	private int maxScrollRows() {
		return Math.max(0, this.slots.size() - this.visibleRows);
	}

	/** 每一行的位置：{@code listTop + (行号 − 卷起行数) × 行高}。 */
	private void layoutRows() {
		int y = this.listTop - this.scrollRow * this.rowHeight;
		for (Slot slot : this.slots) {
			slot.y = y;
			this.applySlot(slot);
			y += slot.height;
		}
		this.updateScrollbar();
	}

	/** 把行的绝对位置写进控件，并决定是否可见。滚动按整行吸附，所以不会剩半截行。 */
	private void applySlot(Slot slot) {
		int bottom = slot.y + slot.height;
		slot.visible = slot.y >= this.listTop && bottom <= this.listBottom && bottom > this.listTop;

		int controlX = LIST_X + this.labelWidth + LABEL_GAP;
		int resetX = this.rightEdge - this.resetWidth;
		int widgetY = slot.y + (slot.height - WIDGET_H) / 2;
		boolean shown = slot.visible;

		if (slot.mainId >= 0) {
			this.host.moveWidget(slot.mainId, controlX, widgetY);
			this.host.widgetVisible(slot.mainId, shown && !slot.numberMode);
		}
		if (slot.numberId >= 0) {
			this.host.moveWidget(slot.numberId, controlX, widgetY);
			this.host.widgetVisible(slot.numberId, shown && slot.numberMode);
		}
		if (slot.switchId >= 0) {
			this.host.moveWidget(slot.switchId, controlX + this.sliderWidth + SWITCH_GAP, widgetY);
			this.host.widgetVisible(slot.switchId, shown);
		}
		if (slot.resetId >= 0) {
			this.host.moveWidget(slot.resetId, resetX, widgetY);
			this.host.widgetVisible(slot.resetId, shown);
		}
	}

	private void updateScrollbar() {
		if (this.scrollbarId < 0) {
			return;
		}
		int max = this.maxScrollRows();
		this.host.widgetVisible(this.scrollbarId, max > 0);
		if (max <= 0) {
			return;
		}
		int travel = Math.max(0, this.viewHeight() - this.thumbHeight);
		int top = this.listTop + (int) ((long) travel * this.scrollRow / max);
		this.host.moveWidget(this.scrollbarId, this.scrollbarX, top);
	}

	/** 把「卷起行数」钳进 {@code [0, 总行数 − 可见行数]}。只改数值，不碰控件。 */
	private void clampScroll() {
		this.scrollRow = Math.max(0, Math.min(this.scrollRow, this.maxScrollRows()));
	}

	@Override
	public boolean scroll(double mouseX, double mouseY, double delta) {
		if (this.maxScrollRows() <= 0) {
			return false;
		}
		if (mouseY < this.listTop || mouseY > this.listBottom) {
			return false;
		}
		int before = this.scrollRow;
		int step = (int) Math.round(delta * 2);
		this.scrollRow = Math.max(0, Math.min(this.maxScrollRows(), this.scrollRow - step));
		if (this.scrollRow != before) {
			this.layoutRows();
		}
		return true;
	}

	private boolean scrollbarHit(double mouseX, double mouseY) {
		return mouseX >= this.scrollbarX - 2
				&& mouseX <= this.scrollbarX + SCROLLBAR_WIDTH + 2
				&& mouseY >= this.listTop && mouseY <= this.listBottom;
	}

	/** 点轨道 / 拖滑块：把指针位置换算成卷起行数（始终是整数）。 */
	private boolean scrollFromPointer(double mouseY) {
		int max = this.maxScrollRows();
		if (max <= 0) {
			return false;
		}
		int travel = Math.max(1, this.viewHeight() - this.thumbHeight);
		double ratio = (mouseY - this.listTop - this.thumbHeight / 2.0) / travel;
		int row = Math.max(0, Math.min(max, (int) Math.round(ratio * max)));
		if (row != this.scrollRow) {
			this.scrollRow = row;
			this.layoutRows();
		}
		return true;
	}

	// ------------------------------------------------------------------ 其余接口

	@Override
	public boolean beforeKey(int keyCode) {
		if (keyCode != KEY_ESCAPE || this.activeNumber == null) {
			return false;
		}
		this.cancelNumber(this.activeNumber);
		return true;
	}

	@Override
	public boolean key(int keyCode, int scanCode, int modifiers) {
		// 控件全部是原版 widget，键盘导航交给游戏；这里只兜底
		return false;
	}

	@Override
	public boolean click(double mouseX, double mouseY, int button) {
		// 点别处不再提交 / 变回滑条：数字输入模式只在回车、切换按钮、Esc 时结束。
		if (this.scrollbarHit(mouseX, mouseY)) {
			this.dragging = true;
			return this.scrollFromPointer(mouseY);
		}
		return false;
	}

	@Override
	public boolean drag(double mouseX, double mouseY, int button) {
		if (!this.dragging && !this.scrollbarHit(mouseX, mouseY)) {
			return false;
		}
		this.dragging = true;
		return this.scrollFromPointer(mouseY);
	}

	@Override
	public boolean release(double mouseX, double mouseY, int button) {
		boolean was = this.dragging;
		this.dragging = false;
		return was;
	}

	@Override
	public void close() {
		// 「只要输入框」的行没有切换键这种确认动作：关界面就是保存点。
		// 合法内容写回配置，非法内容整条丢弃，显示也恢复成原值。
		this.commitBoxRows();
		for (Slot slot : this.slots) {
			if (slot.boxOnly && !slot.numberValid) {
				this.cancelNumber(slot);
			}
		}
		if (this.activeNumber != null && !this.commitNumber(this.activeNumber)) {
			// 关界面时非法输入直接丢弃，不写回配置
			this.cancelNumber(this.activeNumber);
		}
		if (this.spec.onSave != null) {
			this.spec.onSave.run();
		}
	}

	/** 一行在屏幕上的位置与它挂的控件。 */
	private static final class Slot {
		final ConfigSpec.Row row;
		/** 本行是否「只要输入框」：没有滑条与切换键，输入框常驻可见。 */
		final boolean boxOnly;
		int y;
		int height;
		int mainId = -1;
		int resetId = -1;
		int switchId = -1;
		int numberId = -1;
		boolean numberMode;
		boolean numberValid = true;
		String numberError;
		/** 字符串行的校验结果；{@code null} 表示合法（或这行不是字符串行）。 */
		String stringError;
		/** 前置项是否满足；不满足时控件置灰、标签变暗、悬停给红字提示。 */
		boolean available = true;
		boolean visible;

		Slot(ConfigSpec.Row row) {
			this.row = row;
			this.boxOnly = row.onlyBox();
		}
	}
}
