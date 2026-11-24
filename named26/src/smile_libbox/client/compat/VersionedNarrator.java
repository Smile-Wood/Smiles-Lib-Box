package smile_libbox.client.compat;

import com.mojang.text2speech.Narrator;

/**
 * 26.2 的 {@link Narrator} 基类：这一代接口只剩 {@code say(String, boolean, float)}，
 * 两参数版本已经不存在。
 *
 * <p>与 1.20.x~1.21.x 的 {@code VersionedNarrator} 保持同一组 protected 契约
 * （{@link #fallback()} / {@link #fallbackSay(String, boolean, float)} /
 * {@link #onSay(String, boolean, float)}），所以使用方的 {@code SystemNarrator}
 * 一个 jar 就能同时跑在两代命名空间上。本类只依赖 text2speech，不碰 MC 类。</p>
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

	/** 26.2 的 Narrator 只有三参数 say。 */
	protected final void fallbackSay(String message, boolean interrupt, float volume) {
		this.fallback.say(message, interrupt, volume);
	}

	/** 稳定回调：{@code volume} 恒为 0~1。 */
	protected abstract void onSay(String message, boolean interrupt, float volume);

	@Override
	public void say(String message, boolean interrupt, float volume) {
		this.onSay(message, interrupt, volume);
	}

	/** 兼容使用方内部可能仍调用的两参数形式，转发到同一个回调。 */
	public void say(String message, boolean interrupt) {
		this.onSay(message, interrupt, 1.0F);
	}
}
