package smile_libbox.client.mixin;

import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import smile_libbox.api.event.InputEvents;

/**
 * 把界面按键转成固定回调。1.21.9 起 {@code keyPressed} 参数从三个 int 变成 {@code KeyEvent}，
 * 由库按版本各编译一份注入点，refmap 天然对应本 jar 的目标版本。
 */
@Mixin(Screen.class)
public abstract class LibScreenKeyMixin {

	//? if >=1.21.9 {
	/*@Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
	private void libbox$screenKey(net.minecraft.client.input.KeyEvent event,
			CallbackInfoReturnable<Boolean> cir) {
		if (InputEvents.dispatchScreenKey(this, event.key(), event.scancode(), event.modifiers())) {
			cir.setReturnValue(true);
		}
	}
	*///?} else {
	@Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
	private void libbox$screenKey(int keyCode, int scanCode, int modifiers,
			CallbackInfoReturnable<Boolean> cir) {
		if (InputEvents.dispatchScreenKey(this, keyCode, scanCode, modifiers)) {
			cir.setReturnValue(true);
		}
	}
	//?}
}
