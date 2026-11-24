package smile_libbox.client.mixin;

import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import smile_libbox.api.event.InputEvents;

/**
 * 把鼠标按键转成固定回调。1.21.9 起 {@code onPress(long,int,int,int)} 换成
 * {@code onButton(long, MouseButtonInfo, int)}，由库按版本各编译一份注入点。
 */
@Mixin(MouseHandler.class)
public abstract class LibMouseMixin {

	//? if >=1.21.9 {
	/*@Inject(method = "onButton", at = @At("HEAD"), cancellable = true)
	private void libbox$mouseButton(long window, net.minecraft.client.input.MouseButtonInfo info,
			int action, CallbackInfo ci) {
		if (InputEvents.dispatchMouseButton(window, info.button(), action)) {
			ci.cancel();
		}
	}
	*///?} else {
	@Inject(method = "onPress", at = @At("HEAD"), cancellable = true)
	private void libbox$mouseButton(long window, int button, int action, int modifiers,
			CallbackInfo ci) {
		if (InputEvents.dispatchMouseButton(window, button, action)) {
			ci.cancel();
		}
	}
	//?}
}
