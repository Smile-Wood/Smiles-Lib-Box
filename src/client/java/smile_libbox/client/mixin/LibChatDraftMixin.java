package smile_libbox.client.mixin;

import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import smile_libbox.api.event.ChatDraftEvents;
import smile_libbox.api.ui.Ui;

/**
 * 聊天框草稿的版本适配注入点。
 *
 * <p>输入框是 {@code EditBox}（MC 类型）且 {@code handleChatInput} 的返回值在 1.20.6
 * 从 {@code boolean} 变成 {@code void}，所以这类注入只能由库按版本各编译一份；使用方通过
 * {@link ChatDraftEvents} 注册纯 Java 回调，不需要认识任何 MC 类，仍是单 jar。</p>
 *
 * <p>三个注入点在 1.20.1~1.21.11 全部核实过：{@code ChatScreen} 覆写了 {@code init()}
 * 与 {@code removed()}，字段名统一为 {@code input}。{@code removed()} 由
 * {@code Minecraft#setScreen} 在界面被换掉时调用，因此关闭聊天框、断开服务器、被顶号
 * 三种情况都会把文本交给使用方落盘。</p>
 *
 * <p><b>发送与关闭必须区分开</b>：原版 {@code handleChatInput} 只发包、<b>不清空</b>
 * {@code EditBox}（1.20.1 字节码 78~79 是 {@code iconst_1 / ireturn}；1.21.11 是
 * {@code return}），随后 {@code keyPressed} 才 {@code setScreen(null)}。若直接按
 * {@code removed()} 里的输入框内容落盘，刚发出去的那条消息会被当成草稿存下来，下次打开
 * 聊天框又原样出现。1.21.x 原版用 {@code exitReason == DONE} 规避（{@code shouldDiscardDraft}），
 * 1.20.x 没有这个字段，所以这里统一用「提交时记下文本，关闭时发现没变就不存」这一招，
 * 跨全部版本通用。</p>
 *
 * <p>原版 26.2 自带会话内草稿；这里只在输入框为空时才回填，避免覆盖原版刚保存的内容。</p>
 */
@Mixin(ChatScreen.class)
public abstract class LibChatDraftMixin {

	@Shadow
	protected EditBox input;

	/** 已提交（真正发出去）的原文，用来在关闭时识别「关闭前那一下是发送」。 */
	@Unique
	private String libbox$submittedText;

	/**
	 * 这一次打开已经落过盘。
	 *
	 * <p>1.21.x 起 {@code ChatScreen#onClose} 会先处理自己的会话内草稿，可能在
	 * {@code removed()} 之前就把输入框清空；只在 {@code removed()} 里读文本会读到空串。
	 * 因此先在 {@code onClose} 的 HEAD 抓（那时文本还在），{@code removed} 只作兜底。</p>
	 */
	@Unique
	private boolean libbox$captured;

	@Inject(method = "init()V", at = @At("TAIL"), require = 1)
	private void libbox$restoreChatDraft(CallbackInfo ci) {
		this.libbox$captured = false;
		if (this.input == null || !this.input.getValue().isEmpty()) {
			// 原版已经有 initial / 草稿 / 命令预填时不动它
			return;
		}
		String draft = ChatDraftEvents.dispatchRestore(Ui.serverAddress());
		if (draft != null && !draft.isEmpty()) {
			this.input.setValue(draft);
		}
	}

	/** 关闭的第一现场：此时输入框里还是玩家写的原文。 */
	@Inject(method = "onClose()V", at = @At("HEAD"), require = 0)
	private void libbox$captureOnClose(CallbackInfo ci) {
		libbox$captureOnce();
	}

	@Inject(method = "removed()V", at = @At("HEAD"), require = 1)
	private void libbox$captureChatDraft(CallbackInfo ci) {
		libbox$captureOnce();
	}

	/** 一次打开只落一次盘：{@code onClose} 抓过之后 {@code removed} 不再插手。 */
	@Unique
	private void libbox$captureOnce() {
		if (this.libbox$captured || this.input == null) {
			return;
		}
		this.libbox$captured = true;
		String value = this.input.getValue();
		if (this.libbox$submittedText != null && this.libbox$submittedText.equals(value)) {
			// 这一轮是「发送后关掉」：输入框里留着的正是刚发出去的文本，不是草稿
			this.libbox$submittedText = null;
			ChatDraftEvents.dispatchCapture(Ui.serverAddress(), "");
			return;
		}
		ChatDraftEvents.dispatchCapture(Ui.serverAddress(), value);
	}

	/**
	 * 提交消息时先把该地址的草稿作废，并记下原文供 {@link #libbox$captureChatDraft} 判重。
	 *
	 * <p>只认「参数与输入框内容一致」的提交：别的 mod 代发命令（宏、快捷键）时输入框里可能
	 * 还有玩家自己没写完的草稿，那不该被清掉。</p>
	 */
	@Unique
	private void libbox$onInputHandled(String text) {
		if (this.input == null || text == null || text.isEmpty()) {
			return;
		}
		if (!this.input.getValue().equals(text)) {
			return;
		}
		this.libbox$submittedText = text;
		ChatDraftEvents.dispatchCapture(Ui.serverAddress(), "");
	}

	//? if >=1.20.6 {
	/*@Inject(method = "handleChatInput(Ljava/lang/String;Z)V", at = @At("HEAD"), require = 0)
	private void libbox$clearChatDraft(String text, boolean addToHistory, CallbackInfo ci) {
		this.libbox$onInputHandled(text);
	}
	*///?} else {
	@Inject(method = "handleChatInput(Ljava/lang/String;Z)Z", at = @At("HEAD"), require = 0)
	private void libbox$clearChatDraft(String text, boolean addToHistory,
			CallbackInfoReturnable<Boolean> cir) {
		this.libbox$onInputHandled(text);
	}
	//?}
}
