package smile_libbox.api.ui;

/**
 * 使用方界面：只描述「画什么、怎么响应输入」，不继承任何 Minecraft 类。
 *
 * <p>库提供一个真实的 {@code Screen} 子类作为宿主，把生命周期转发到这里。
 * 实现本接口的类放在使用方的单 jar 里，常量池不含 MC 类型，因此同一个 jar
 * 可以同时装到 intermediary 与 named 命名空间的游戏上。</p>
 */
public interface UiScreen {

	/** 已经翻译好的标题文本。 */
	String title();

	/** 父界面句柄（由 {@link Ui#currentScreen()} 取得），可为 {@code null}。 */
	Object parent();

	boolean pause();

	void init(UiHost host);

	void render(UiCanvas canvas, UiHost host, int mouseX, int mouseY, float partialTick);

	/**
	 * 控件画完之后的浮层绘制（默认不画）。
	 *
	 * <p>{@link #render} 发生在原版 widget 之前，画在这里的东西会被按钮/输入框盖住；
	 * tooltip 这类必须浮在控件之上的内容用这个钩子。宿主会在 {@code super.render} /
	 * {@code super.extractRenderState} 之后调用它。</p>
	 */
	default void renderOverlay(UiCanvas canvas, UiHost host, int mouseX, int mouseY,
			float partialTick) {
	}

	boolean key(int keyCode, int scanCode, int modifiers);

	/**
	 * 键盘事件在交给原版 {@code Screen} 之前先问一次。
	 *
	 * <p>原版 {@code Screen} 会抢先处理 Esc 直接关界面，焦点里的输入框根本收不到；
	 * 数字输入框靠这个钩子把 Esc 拦成「取消输入」。默认返回 false，其它界面不受影响。</p>
	 */
	default boolean beforeKey(int keyCode) {
		return false;
	}

	boolean click(double mouseX, double mouseY, int button);

	/**
	 * 按住鼠标移动。默认不处理；滑块这类控件覆写它来实现拖动。
	 *
	 * <p>坐标是绝对坐标，使用方不用关心 {@code dragX/dragY} —— 那些值在不同 MC
	 * 版本里语义有过变化，直接按绝对位置算比例更稳。</p>
	 */
	default boolean drag(double mouseX, double mouseY, int button) {
		return false;
	}

	/** 鼠标松开。配合 {@link #drag} 结束拖动状态。 */
	default boolean release(double mouseX, double mouseY, int button) {
		return false;
	}

	boolean scroll(double mouseX, double mouseY, double delta);

	/** 关闭界面时调用（点「完成」或按 Esc）。默认由宿主转回父界面。 */
	void close();
}
