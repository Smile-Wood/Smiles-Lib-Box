package smile_libbox.api.ui;

/**
 * tooltip 中的一行：文字 + 可选颜色。
 *
 * <p>存在的理由：同一个 tooltip 里可能需要两种颜色 —— 比如「前置项没开」时，
 * 上面一行是这一项本身的说明（原版灰白），最后一行才是红色的「要启用此项，
 * 你需要开启 XXX」。整条同色的 {@link UiHost#tooltip(int, int, java.util.List, int)}
 * 表达不了这种情况。</p>
 *
 * <p>纯 Java 值对象，不含任何 MC 类型；颜色是 ARGB，{@code 0} 表示跟随原版默认色。</p>
 */
public final class UiTooltip {

	/** 已经翻译好的文字。 */
	public final String text;
	/** ARGB；{@code 0} = 原版 tooltip 默认文字色。 */
	public final int argb;

	private UiTooltip(String text, int argb) {
		this.text = text == null ? "" : text;
		this.argb = argb;
	}

	public static UiTooltip of(String text) {
		return new UiTooltip(text, 0);
	}

	public static UiTooltip colored(String text, int argb) {
		return new UiTooltip(text, argb);
	}
}
