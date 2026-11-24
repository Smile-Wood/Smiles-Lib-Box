package smile_libbox.client.mixin;

import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import smile_libbox.api.event.InputEvents;
import smile_libbox.client.compat.Named26Hooks;

/**
 * 26.2 的全局鼠标按键注入点：{@code MouseHandler#onButton(long, MouseButtonInfo, int)}。
 *
 * <p>只有走全局鼠标事件，才能在任何界面（含原版界面）里拦下自定义退出键；
 * 注入点按 26.2 named 签名编译，不依赖 refmap。</p>
 */
@Mixin(MouseHandler.class)
public abstract class LibMouseMixin {

	@Inject(method = "onButton(JLnet/minecraft/client/input/MouseButtonInfo;I)V",
			at = @At("HEAD"), cancellable = true, require = 1)
	private void libbox$mouseButton(long window, MouseButtonInfo info, int action,
			CallbackInfo ci) {
		Named26Hooks.first("MouseHandler.onButton");
		if (InputEvents.dispatchMouseButton(window, info.button(), action)) {
			ci.cancel();
		}
	}
}
