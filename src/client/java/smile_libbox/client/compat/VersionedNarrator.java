package smile_libbox.client.compat;

import com.mojang.text2speech.Narrator;

/**
 * 跨版本 {@link Narrator} 基类：1.21.5 起 {@code say} 多了一个音量参数。
 *
 * <p>库按游戏版本编译这个类，实现该版本 text2speech 接口要求的签名，再统一转发到
 * {@link #onSay(String, boolean, float)}。使用方只需要实现这一个回调，
 * 单 jar 即可覆盖 1.20.1~1.21.11。</p>
 */
public abstract class VersionedNarrator implements Narrator {

	private final Narrator fallback;

	protected VersionedNarrator(Narrator fallback) {
		this.fallback = fallback;
	}

	/** 被包装的原版讲述人；系统语音不可用时应回退到它。 */
	protected final Narrator fallback() {
		return this.fallback;
	}

	/** 按当前版本签名调用原版讲述人，音量在低版本被忽略。 */
	protected final void fallbackSay(String message, boolean interrupt, float volume) {
		//? if >=1.21.5 {
		/*this.fallback.say(message, interrupt, volume);
		*///?} else {
		this.fallback.say(message, interrupt);
		//?}
	}

	/** 稳定回调：{@code volume} 恒为 0~1，低版本固定传 1.0。 */
	protected abstract void onSay(String message, boolean interrupt, float volume);

	//? if >=1.21.5 {
	/*@Override
	public void say(String message, boolean interrupt, float volume) {
		this.onSay(message, interrupt, volume);
	}

	// 供使用方内部直接调用，保持两版一致
	public void say(String message, boolean interrupt) {
		this.onSay(message, interrupt, 1.0F);
	}
	*///?} else {
	@Override
	public void say(String message, boolean interrupt) {
		this.onSay(message, interrupt, 1.0F);
	}
	//?}
}
