package smile_libbox.client.mixin;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import smile_libbox.api.event.InputEvents;
import smile_libbox.client.compat.Named26Hooks;

/**
 * 26.2 的界面按键注入点：{@code Screen#keyPressed(KeyEvent)}。
 *
 * <p>1.21.9 起三个 int 被合成 {@code KeyEvent} record，26.2 里进一步只剩这一个重载。
 * 这里把 key / scancode / modifiers 拆回固定回调，使用方（自定义退出键）不需要认识
 * {@code KeyEvent}，也不需要自己写 mixin。</p>
 */
@Mixin(Screen.class)
public abstract class LibScreenKeyMixin {

	@Inject(method = "keyPressed(Lnet/minecraft/client/input/KeyEvent;)Z",
			at = @At("HEAD"), cancellable = true, require = 1)
	private void libbox$screenKey(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
		Named26Hooks.first("Screen.keyPressed");
		if (InputEvents.dispatchScreenKey(this, event.key(), event.scancode(),
				event.modifiers())) {
			cir.setReturnValue(true);
		}
	}
}
