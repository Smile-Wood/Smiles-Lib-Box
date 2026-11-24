package smile_libbox.api.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * 配置界面的纯 Java 数据描述：只填数据，渲染由库负责。
 *
 * <p>这是「逐条代码定义」那一种写法；同样的结构也能由 {@link ConfigLayout}
 * 从 JSON 解析出来，两种定义的渲染结果完全一致 —— 界面只有一个实现，
 * 定义只是入口不同。</p>
 *
 * <p>所有文字都是已经取好值的 {@link String}：翻译键由 {@link Ui#tr(String, Object...)}
 * 先转成字符串，避免 {@code Component} 这类 MC 类型出现在描述符里。</p>
 */
public final class ConfigSpec {

	public final String title;
	public final Object parent;
	public final Runnable onSave;
	public final List<Page> pages = new ArrayList<>();

	/**
	 * 右下角的「完成」按钮是否显示，默认 {@code true}。
	 *
	 * <p>有些模组会在非常规的地方调用这套界面（嵌入式面板、别的模组转进来的子页面），
	 * 那时「完成 = 关闭整个界面」未必是对的，可以关掉它，改由 {@link #doneAction} 自己收尾。</p>
	 *
	 * <p>想一次关掉「完成 + 全部重置」两颗系统按钮，用 {@link #showSystemButtons(boolean)}。</p>
	 */
	public boolean showDone = true;
	/** 「完成」按钮的文字，{@code null} 用内置的「完成」。 */
	public String doneLabel;
	/** 点「完成」时执行的动作；{@code null} 表示关闭界面（默认行为）。 */
	public Runnable doneAction;

	/**
	 * 每行右侧的重置键与右下角的「全部重置」是否显示，默认 {@code true}。
	 *
	 * <p>关掉之后控件列会一直延伸到右端（滑条更长、切换键跟着右移），
	 * 适合值不该被界面复位、或者本来就没有默认值的模组。</p>
	 */
	public boolean showReset = true;

	/**
	 * @param title 已翻译的窗口标题
	 * @param parent 父界面句柄，可为 {@code null}
	 * @param onSave 点「完成」或关闭界面时调用，用来把值落盘；可为 {@code null}
	 */
	public ConfigSpec(String title, Object parent, Runnable onSave) {
		this.title = title;
		this.parent = parent;
		this.onSave = onSave;
	}

	/** 是否显示右下角的「完成」。{@code false} 时只剩 Esc 能退出。 */
	public ConfigSpec showDone(boolean show) {
		this.showDone = show;
		return this;
	}

	/** 换掉「完成」按钮的文字与点击动作（传 {@code null} 动作表示仍走「关闭界面」）。 */
	public ConfigSpec doneAction(String label, Runnable action) {
		this.doneLabel = label;
		this.doneAction = action;
		return this;
	}

	/** 是否显示每行的重置键与「全部重置」。关掉后控件列向右延长。 */
	public ConfigSpec showReset(boolean show) {
		this.showReset = show;
		return this;
	}

	/**
	 * 一次决定这颗页面上的**系统按钮**（右下角「完成」+「全部重置」）显不显示。
	 *
	 * <p>传 {@code false} 等于 {@code showDone(false).showReset(false)}：整条底栏交给
	 * {@link Page#bottom} 自己安排，模组可以用自己的按钮把这一行填满（也可以一个都不放，
	 * 库会把这一行连同高度一起收掉，列表顺势向下多显示一行）。</p>
	 *
	 * <p>只想去掉其中一颗时仍用 {@link #showDone(boolean)} / {@link #showReset(boolean)}。</p>
	 */
	public ConfigSpec showSystemButtons(boolean show) {
		this.showDone = show;
		this.showReset = show;
		return this;
	}

	/**
	 * 这颗页面上是否有系统按钮：{@link #showDone} 与 {@link #showReset} 任一为真。
	 *
	 * <p>界面据此决定底栏要不要留位置 —— 两颗都关掉、模组也没往 {@link Page#bottom}
	 * 放东西时，底栏整条不占高度。</p>
	 */
	public boolean hasSystemButtons() {
		return this.showDone || this.showReset;
	}

	public Page page(String title) {
		Page page = new Page(title);
		this.pages.add(page);
		return page;
	}

	public static final class Page {
		public final String title;
		public final List<Row> rows = new ArrayList<>();
		/** 页面底部的动作按钮，排在左侧。 */
		public final List<Footer> footers = new ArrayList<>();
		/**
		 * 最下面那条**自定义区域**：位置、宽度、要放几个按钮与几行状态文字全由使用方
		 * 决定，库只负责照做（见 {@link Bottom}）。
		 */
		public final Bottom bottom = new Bottom();

		Page(String title) {
			this.title = title;
		}

		/** 底部动作按钮。文字固定。 */
		public Page footer(String label, Runnable onClick) {
			return this.footer(() -> label, () -> true, onClick);
		}

		/**
		 * 底部动作按钮。文字与可用状态每次绘制重新取，用于「音色：小艺」这类动态内容、
		 * 以及「没装 Carpet 就置灰」这类运行时判定。
		 */
		public Page footer(Supplier<String> label, Supplier<Boolean> enabled, Runnable onClick) {
			this.footers.add(new Footer(label, enabled, onClick));
			return this;
		}

		/** 开关。{@code key} 是写回配置文件用的标识，可为 {@code null}。 */
		public Page toggle(String key, String label, String tooltip, boolean value,
				boolean defaultValue, Consumer<Boolean> save) {
			Row row = new Row(Kind.TOGGLE, label, tooltip);
			row.key = key;
			row.booleanValue = value;
			row.booleanDefault = defaultValue;
			row.booleanSave = save;
			this.rows.add(row);
			return this;
		}

		/** 整数滑条。 */
		public Page slider(String key, String label, String tooltip, int value, int min, int max,
				int defaultValue, Consumer<Integer> save) {
			Row row = new Row(Kind.SLIDER, label, tooltip);
			row.key = key;
			row.intValue = value;
			row.min = min;
			row.max = max;
			row.intDefault = defaultValue;
			row.intSave = save;
			this.rows.add(row);
			return this;
		}

		/**
		 * 只有输入框的整数行：不画滑条、也不画右侧的 T/≡ 切换键，控件列整个让给数字框。
		 *
		 * <p>等价于 {@code slider(...).last().boxOnly()}，适合范围大、用滑条根本拖不准的项
		 * （秒数、字数这类）。键入时实时校验、非法标红；内容在回车 / 关界面时写回配置。</p>
		 */
		public Page number(String key, String label, String tooltip, int value, int min, int max,
				int defaultValue, Consumer<Integer> save) {
			this.slider(key, label, tooltip, value, min, max, defaultValue, save);
			Row row = this.last();
			if (row != null) {
				row.boxOnly();
			}
			return this;
		}

		/** 纯说明文字，不占控件。 */
		public Page text(String value) {
			this.rows.add(new Row(Kind.TEXT, value, null));
			return this;
		}

		/**
		 * 字符串输入框：直接键入内容。
		 *
		 * <p>不写正则就什么都收；写了正则（{@link Row#regex(String, String)} /
		 * {@link Row#regexFile(String)}）就实时校验，不合法的内容标红，鼠标悬停时
		 * 用红字 tooltip 说「不合法的字符串！仅支持{正则名}」。合法内容每敲一个字就写回，
		 * 非法内容不写回（关界面时丢弃）。</p>
		 *
		 * @param value 当前值，{@code null} 当空串
		 * @param defaultValue 重置用的默认值，{@code null} 当空串
		 * @param save 值变化时的写回口子，可为 {@code null}
		 */
		public Page string(String key, String label, String tooltip, String value,
				String defaultValue, Consumer<String> save) {
			Row row = new Row(Kind.STRING, label, tooltip);
			row.key = key;
			row.stringValue = value == null ? "" : value;
			row.stringDefault = defaultValue == null ? "" : defaultValue;
			row.stringSave = save;
			this.rows.add(row);
			return this;
		}

		/**
		 * 刚添加的那一行，用来追加 {@link Row#regex(String)} 这类可选属性：
		 * {@code page.slider(...).last().regex("[0-9]{1,2}")}。
		 */
		public Row last() {
			return this.rows.isEmpty() ? null : this.rows.get(this.rows.size() - 1);
		}

		/**
		 * 一行「标签 + 按钮」。
		 *
		 * @param buttonLabel 按钮上的文字，每次绘制都会重新取（音色名这类动态内容）
		 */
		public Page action(String key, String label, String tooltip, Supplier<String> buttonLabel,
				Runnable onClick) {
			Row row = new Row(Kind.ACTION, label, tooltip);
			row.key = key;
			row.buttonLabel = buttonLabel;
			row.onClick = onClick;
			this.rows.add(row);
			return this;
		}
	}

	public enum Kind { TOGGLE, SLIDER, STRING, TEXT, ACTION }

	/** 底部动作按钮。 */
	public static final class Footer {
		public final Supplier<String> label;
		public final Supplier<Boolean> enabled;
		public final Runnable onClick;

		Footer(Supplier<String> label, Supplier<Boolean> enabled, Runnable onClick) {
			this.label = label;
			this.enabled = enabled;
			this.onClick = onClick;
		}
	}

	/**
	 * 配置界面**最下面那一行**：与「全部重置」「完成」同一行，由使用方往里加按钮与状态文字。
	 *
	 * <p>这一行原本只放系统按钮，现在整行都是可自定义的：使用方加的按钮从列表左边界
	 * 往右排（或按 {@link Align#RIGHT} 从右往左排），系统按钮仍钉在右端，两边撞上时
	 * 自建按钮收窄，不会互相压住。**不会多占一行**，列表的可视行数不受影响。</p>
	 *
	 * <p>宽度按文字自动算（统一口径，不会一颗宽一颗窄），间距固定 {@link #GAP}；
	 * 按钮文字与禁用态**每帧重新取**，所以「执行中置灰」「剩余 N 个」这类状态直接写
	 * {@code Supplier} 即可，不必自己刷新控件。</p>
	 */
	public static final class Bottom {

		/** 一项：按钮或只读文字。 */
		public enum Kind { BUTTON, TEXT }

		/**
		 * 一行里各项的堆叠方向。
		 *
		 * <p>{@link #LEFT} 从列表左边界往右排（默认）；{@link #RIGHT} 从该行右端往左排，
		 * 所以先加的按钮在最右边。两种可以混着用 —— 比如左边放操作按钮、右边放「文档」，
		 * 库会让它们各占一头，撞上时收窄后加的这颗。</p>
		 */
		public enum Align { LEFT, RIGHT }

		/** 同一行里两颗按钮之间的间距。 */
		public static final int GAP = 6;
		/** 按钮左右内边距之和：宽度 = 文字宽 + 它。 */
		public static final int BUTTON_PADDING = 16;
		/** 按钮最小宽度。 */
		public static final int MIN_BUTTON_WIDTH = 40;

		public final List<Item> items = new ArrayList<>();

		/**
		 * 接下来添加的项默认靠哪边排。改它只影响**之后**的 {@code button/link}，
		 * 已经加进去的项不变；想单独改某一颗就用 {@code last().align(...)}。
		 */
		public Align align = Align.LEFT;

		/**
		 * 改默认对齐：之后的按钮从左往右排（{@link Align#LEFT}）还是从右往左排
		 * （{@link Align#RIGHT}）。链式写法通常紧跟在 {@code bottom} 后面。
		 */
		public Bottom align(Align value) {
			this.align = value == null ? Align.LEFT : value;
			return this;
		}

		/**
		 * 放一颗原版按钮。
		 *
		 * @param label 已翻译的文字，每帧重新取；宽度按它算
		 * @param enabled 每帧重新取；{@code null} 表示恒可用
		 * @param onClick 点击回调
		 */
		public Bottom button(Supplier<String> label, Supplier<Boolean> enabled,
				Runnable onClick) {
			this.items.add(new Item(Kind.BUTTON, this.align, label, enabled, onClick, 0));
			return this;
		}

		/** 指定对齐的按钮，不受 {@link #align} 影响。 */
		public Bottom button(Align where, Supplier<String> label,
				Supplier<Boolean> enabled, Runnable onClick) {
			this.items.add(new Item(Kind.BUTTON, where, label, enabled, onClick, 0));
			return this;
		}

		/** 恒可用的按钮。 */
		public Bottom button(Supplier<String> label, Runnable onClick) {
			return this.button(this.align, label, null, onClick);
		}

		/** 恒可用、且指定对齐的按钮。 */
		public Bottom button(Align where, Supplier<String> label, Runnable onClick) {
			return this.button(where, label, null, onClick);
		}

		/**
		 * 一颗「打开网页」的按钮：点它用系统浏览器打开 {@code url}（{@link Ui#openLink}）。
		 * 需要动态可用性时改用 {@link #button(Supplier, Supplier, Runnable)} + {@link Ui#openLink}。
		 */
		public Bottom link(Supplier<String> label, String url) {
			return this.button(this.align, label, null, () -> Ui.openLink(url));
		}

		/** 指定对齐的网页按钮。 */
		public Bottom link(Align where, Supplier<String> label, String url) {
			return this.button(where, label, null, () -> Ui.openLink(url));
		}

		/**
		 * 刚才加的那一项，用来补一个「为什么灰着」的提示：
		 * {@code bottom.button(...).last().disabledTip("需要先登录")}。
		 *
		 * <p>不补就用内置的「该选项暂不可用」。</p>
		 */
		public Item last() {
			return this.items.isEmpty() ? null : this.items.get(this.items.size() - 1);
		}

		/**
		 * 只读文字，用画布画（在原版控件之上），适合「有进行中的批量任务」这类状态提示。
		 * 默认接在左对齐按钮之后；{@link Align#RIGHT} 时贴该行右端。超宽时自动截断。
		 *
		 * @param argb 文字颜色，如 {@code 0xFFFF5555} 是红字；{@code 0} 用原版默认色
		 */
		public Bottom text(Supplier<String> text, int argb) {
			this.items.add(new Item(Kind.TEXT, this.align, text, null, null, argb));
			return this;
		}

		/** 指定对齐的只读文字。 */
		public Bottom text(Align where, Supplier<String> text, int argb) {
			this.items.add(new Item(Kind.TEXT, where, text, null, null, argb));
			return this;
		}
	}

	/** 底部那一行里的一项。横坐标与宽度由界面在排版时算出，不在这里存。 */
	public static final class Item {
		/** 内置的禁用提示键：没配 {@link #disabledTip} 时用它。 */
		public static final String DEFAULT_DISABLED_TIP = "smile_libbox.gui.unavailable";

		public final Bottom.Kind kind;
		/** 靠哪边排。加进来时的对齐方式，之后可以 {@link #align(Bottom.Align)} 改。 */
		public Bottom.Align align;
		public final Supplier<String> label;
		public final Supplier<Boolean> enabled;
		public final Runnable onClick;
		public final int argb;
		/**
		 * 按钮灰着时鼠标悬停显示的话（已翻译）；{@code null} / 空串用
		 * {@link #DEFAULT_DISABLED_TIP}「该选项暂不可用」。
		 */
		public String disabledTip;

		Item(Bottom.Kind kind, Bottom.Align align, Supplier<String> label,
				Supplier<Boolean> enabled, Runnable onClick, int argb) {
			this.kind = kind;
			this.align = align == null ? Bottom.Align.LEFT : align;
			this.label = label;
			this.enabled = enabled;
			this.onClick = onClick;
			this.argb = argb;
		}

		/** 单独改这一项的对齐，链式：{@code last().align(Bottom.Align.RIGHT)}。 */
		public Item align(Bottom.Align value) {
			this.align = value == null ? Bottom.Align.LEFT : value;
			return this;
		}

		/** 指定「为什么点不了」，链式。 */
		public Item disabledTip(String tip) {
			this.disabledTip = tip;
			return this;
		}

		/** 当前文字；{@code null} 当空串处理。 */
		public String label() {
			String text = this.label == null ? null : this.label.get();
			return text == null ? "" : text;
		}

		/** 当前是否可用；没写 {@code enabled} 就是恒可用。 */
		public boolean enabled() {
			return this.enabled == null || Boolean.TRUE.equals(this.enabled.get());
		}

		/** 禁用时显示的话；没配就是内置的「该选项暂不可用」。 */
		public String disabledTipText() {
			return this.disabledTip == null || this.disabledTip.isEmpty()
					? Ui.tr(DEFAULT_DISABLED_TIP) : this.disabledTip;
		}
	}
	public static final class Row {

		/** 整数输入的默认正则：允许负号，但必须至少有一位数字。 */
		public static final String DEFAULT_NUMBER_REGEX = "-?[0-9]+";

		public final Kind kind;
		public final String label;
		public final String tooltip;
		/** 配置文件里的标识；TEXT 行为 {@code null}。 */
		public String key;
		public boolean booleanValue;
		public boolean booleanDefault;
		public Consumer<Boolean> booleanSave;
		public int intValue;
		public int min;
		public int max;
		public int intDefault;
		public Consumer<Integer> intSave;
		/** 字符串行的当前值与默认值（{@link Kind#STRING}）。 */
		public String stringValue = "";
		public String stringDefault = "";
		public Consumer<String> stringSave;
		/**
		 * 输入框的合法性正则（整串匹配）。
		 *
		 * <p>SLIDER 缺省是 {@link #DEFAULT_NUMBER_REGEX}（整数）；STRING 缺省是
		 * {@code null}，表示什么都收。JSON 定义里对应 {@code "regex"} 字段。</p>
		 */
		public String regex;
		/**
		 * 正则的人话名字（已翻译），出错提示写成「不合法的字符串！仅支持{名字}」。
		 * {@code null} 表示用内置名字（数字行「整数」、字符串行「文本」）。
		 * JSON 里对应 {@code "regexName"}。
		 */
		public String regexLabel;
		public Supplier<String> buttonLabel;
		public Runnable onClick;
		/**
		 * 前置选项：必须是一行 {@link Kind#TOGGLE}。它为关时本行整体禁用（控件变灰、
		 * 不响应点击），鼠标悬停时用红字提示先开启它。
		 *
		 * <p>一个前置可以带任意多个从属行（每个行各自指回它）；{@code null} 表示本行
		 * 既是前置项、也不受约束。JSON 定义里对应 {@code "requires": "<前置项的 key>"}。</p>
		 */
		public Row requires;

		/**
		 * 数值行是否「只要输入框」：不画滑条、不画 T/≡ 切换键，控件列整条给数字框。
		 *
		 * <p>键入手感与滑条行的数字框一致：实时校验、非法标红；区别是内容只在
		 * <b>回车 / 关界面 / 点「完成」</b>时写回配置，键入过程中不动配置。
		 * 关界面时非法内容直接丢弃，配置保持原值（显示也恢复成原值）。</p>
		 *
		 * <p>只对 {@link Kind#SLIDER} 生效；对其它行调用什么也不做。</p>
		 */
		public boolean boxOnly;

		Row(Kind kind, String label, String tooltip) {
			this.kind = kind;
			this.label = label;
			this.tooltip = tooltip;
			// 数字行默认「只收整数」；字符串行默认「什么都收」，不写正则就不校验
			this.regex = kind == Kind.SLIDER ? DEFAULT_NUMBER_REGEX : null;
		}

		/** 链式指定前置项（必须为开关行；传非开关行会被忽略）。 */
		public Row requires(Row parent) {
			if (parent != null && parent.kind == Kind.TOGGLE && parent != this) {
				this.requires = parent;
			}
			return this;
		}

		/** 前置是否已满足。没有前置的行恒为可用。 */
		public boolean available() {
			return this.requires == null || this.requires.booleanValue;
		}

		/**
		 * 让这一行只留输入框（链式，只对 {@link Kind#SLIDER} 有意义）。
		 *
		 * <pre>{@code page.slider("ttl", "草稿保留", tip, 600, 60, 3600, 600, save)
		 *     .last().boxOnly();}</pre>
		 *
		 * <p>滑条和切换键都不再创建，控件列宽度全给数字框；回车 / 关界面时写回配置。</p>
		 */
		public Row boxOnly() {
			if (this.kind == Kind.SLIDER) {
				this.boxOnly = true;
			}
			return this;
		}

		/** 是否是「只要输入框」的数值行（非数值行永远为 {@code false}）。 */
		public boolean onlyBox() {
			return this.kind == Kind.SLIDER && this.boxOnly;
		}

		/** 改这一行的数字输入正则（链式，只在 SLIDER 行有意义）。 */
		public Row regex(String value) {
			this.regex = value;
			return this;
		}

		/**
		 * 改正则的同时给它一个人话名字，出错提示会写成
		 * 「不合法的字符串！仅支持{名字}」——名字是模组自己的说法，比如「本地文件夹」。
		 *
		 * <pre>{@code page.slider(...).last().regex("[A-Za-z]:\\\\.*", "本地文件夹");}</pre>
		 */
		public Row regex(String value, String label) {
			this.regex = value;
			this.regexLabel = label;
			return this;
		}

		/**
		 * 只接受 {@code .ext} 结尾的文件名（大小写不敏感），提示文案自动变成
		 * 「不合法的字符串！仅支持.txt格式文件」。
		 *
		 * @param extension 扩展名，带不带点都行（{@code "txt"} / {@code ".txt"}）
		 */
		public Row regexFile(String extension) {
			String ext = extension == null ? "" : extension.trim();
			while (ext.startsWith(".")) {
				ext = ext.substring(1);
			}
			this.regex = "(?i).+\\." + Pattern.quote(ext) + "$";
			this.regexLabel = Ui.tr("smile_libbox.gui.regex.file", ext);
			return this;
		}

		/** 出错提示里用的正则名字：自定义了就用自定义的，否则是内置的「整数」/「文本」。 */
		public String regexName() {
			if (this.regexLabel != null && !this.regexLabel.isEmpty()) {
				return this.regexLabel;
			}
			return Ui.tr(this.kind == Kind.STRING
					? "smile_libbox.gui.regex.text" : "smile_libbox.gui.regex.integer");
		}

		/**
		 * 实时校验字符串输入框的内容。
		 *
		 * <p>没写正则就什么都算合法；写了就整串匹配，匹配不上返回 {@code "pattern"}，
		 * 界面据此标红并在悬停时提示「不合法的字符串！仅支持{@link #regexName()}」。
		 * 空串也照正则判（要求非空就写 {@code ".+"}），不额外拦截。正则写错了当作
		 * 「没有约束」，不让界面崩。</p>
		 */
		public String stringError(String raw) {
			String expression = this.regex;
			if (expression == null || expression.isEmpty()) {
				return null;
			}
			try {
				return Pattern.matches(expression, raw == null ? "" : raw) ? null : "pattern";
			} catch (PatternSyntaxException badRegex) {
				return null;
			}
		}

		/**
		 * 实时校验数字输入框的内容。
		 *
		 * <p>返回码只有两类，对应界面上的两条提示模板：</p>
		 * <ul>
		 *   <li>{@code "empty"} / {@code "pattern"} —— 内容不是这条正则能接受的东西，
		 *       提示「不合法的字符串！仅支持{@link #regexName()}」；</li>
		 *   <li>{@code "range_error"} —— 是个数字但超出 {@code min~max}（含溢出 int），
		 *       提示「不合法的数字！仅支持{min}~{max}之间的数据」。</li>
		 * </ul>
		 *
		 * <p>合法时返回 {@code null}。正则由使用方（代码或 JSON）提供，写错了不能让
		 * 界面崩：编译失败时退回「只校验整数」。</p>
		 */
		public String numberError(String raw) {
			String text = raw == null ? "" : raw.trim();
			if (text.isEmpty()) {
				return "empty";
			}
			String expression = this.regex == null || this.regex.isEmpty()
					? DEFAULT_NUMBER_REGEX : this.regex;
			try {
				if (!Pattern.matches(expression, text)) {
					return "pattern";
				}
			} catch (PatternSyntaxException badRegex) {
				// 使用方写了非法正则：跳过这一层，交给下面的整数解析兜底
			}
			int parsed;
			try {
				parsed = Integer.parseInt(text);
			} catch (NumberFormatException notAnInteger) {
				// 纯数字（如 99999999999999）只是溢出 int，按「超出范围」报；
				// 其余（非法正则兜底时遇到的字母）才算字符串不合法
				return isIntegerLiteral(text) ? "range_error" : "pattern";
			}
			return parsed < this.min || parsed > this.max ? "range_error" : null;
		}

		/** 整串是不是一个整数写法（允许负号）。 */
		private static boolean isIntegerLiteral(String text) {
			int start = text.startsWith("-") ? 1 : 0;
			if (start >= text.length()) {
				return false;
			}
			for (int i = start; i < text.length(); i++) {
				if (!Character.isDigit(text.charAt(i))) {
					return false;
				}
			}
			return true;
		}

		/** 这一行能否被 RESET 复位（只有带值的手动配置项可以）。 */
		public boolean resettable() {
			return this.kind == Kind.TOGGLE || this.kind == Kind.SLIDER || this.kind == Kind.STRING;
		}

		public boolean isDefaultValue() {
			return switch (this.kind) {
				case TOGGLE -> this.booleanValue == this.booleanDefault;
				case SLIDER -> this.intValue == this.intDefault;
				case STRING -> this.stringValue.equals(this.stringDefault);
				default -> true;
			};
		}

		/** 复位到默认值，返回是否真的改了。 */
		public boolean resetToDefault() {
			if (this.kind == Kind.TOGGLE) {
				if (this.booleanValue == this.booleanDefault) {
					return false;
				}
				this.booleanValue = this.booleanDefault;
				if (this.booleanSave != null) {
					this.booleanSave.accept(this.booleanValue);
				}
				return true;
			}
			if (this.kind == Kind.SLIDER) {
				if (this.intValue == this.intDefault) {
					return false;
				}
				this.intValue = this.intDefault;
				if (this.intSave != null) {
					this.intSave.accept(this.intValue);
				}
				return true;
			}
			if (this.kind == Kind.STRING) {
				if (this.stringValue.equals(this.stringDefault)) {
					return false;
				}
				this.stringValue = this.stringDefault;
				if (this.stringSave != null) {
					this.stringSave.accept(this.stringValue);
				}
				return true;
			}
			return false;
		}
	}
}
