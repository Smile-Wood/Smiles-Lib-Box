package smile_libbox.client.mixin;

import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import smile_libbox.api.event.ChatDraftEvents;
import smile_libbox.api.ui.Ui;
import smile_libbox.client.compat.Named26Hooks;

/**
 * 26.2 官方名命名空间的聊天框草稿注入点。
 *
 * <p>与 {@code src/client} 里的同名类一一对应，但描述符直接写官方名、无 refmap：
 * 26.2 的 {@code ChatScreen#handleChatInput(String,boolean)} 返回 {@code void}，
 * {@code init()} / {@code removed()} 仍由 ChatScreen 覆写，字段仍叫 {@code input}。</p>
 *
 * <p>发送后 {@code handleChatInput} 同样不清空 {@code EditBox}，{@code removed()} 却会把
 * 输入框内容当草稿落盘 —— 这里用「提交时记原文、关闭时比对」挡掉刚发出去的那条。</p>
 *
 * <p>26.2 原版自带会话内草稿（{@code ChatComponent#saveAsDraft}），但它不跨客户端；
 * 这里只在输入框为空时回填使用方持久化的草稿，两者不冲突。</p>
 */
@Mixin(ChatScreen.class)
public abstract class LibChatDraftMixin {

	@Shadow
	protected EditBox input;

	@Unique
	private String libbox$submittedText;

	/**
	 * 这一次打开已经落过盘。
	 *
	 * <p>26.2 的 {@code ChatScreen#onClose} 会先把自己的会话内草稿交出去、可能顺手清空输入框，
	 * 之后 {@code Minecraft#setScreen(null)} 才触发 {@code removed()}。如果只在 {@code removed()}
	 * 里读输入框，读到的往往已经是空串 —— 「草稿保留没生效」就是这么来的。所以先在
	 * {@code onClose} 的 HEAD 抓一次（那时文本还在），{@code removed} 只作为兜底。</p>
	 */
	@Unique
	private boolean libbox$captured;

	@Inject(method = "init()V", at = @At("TAIL"), require = 1)
	private void libbox$restoreChatDraft(CallbackInfo ci) {
		Named26Hooks.first("ChatScreen.init");
		this.libbox$captured = false;
		if (this.input == null || !this.input.getValue().isEmpty()) {
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
		Named26Hooks.first("ChatScreen.onClose");
		libbox$captureOnce();
	}

	@Inject(method = "removed()V", at = @At("HEAD"), require = 1)
	private void libbox$captureChatDraft(CallbackInfo ci) {
		Named26Hooks.first("ChatScreen.removed");
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
			this.libbox$submittedText = null;
			ChatDraftEvents.dispatchCapture(Ui.serverAddress(), "");
			return;
		}
		ChatDraftEvents.dispatchCapture(Ui.serverAddress(), value);
	}

	@Inject(method = "handleChatInput(Ljava/lang/String;Z)V", at = @At("HEAD"), require = 1)
	private void libbox$clearChatDraft(String text, boolean addToHistory, CallbackInfo ci) {
		Named26Hooks.first("ChatScreen.handleChatInput");
		if (this.input == null || text == null || text.isEmpty()) {
			return;
		}
		if (!this.input.getValue().equals(text)) {
			return;
		}
		this.libbox$submittedText = text;
		ChatDraftEvents.dispatchCapture(Ui.serverAddress(), "");
	}
}
