package smile_libbox.client.mixin;

import net.minecraft.client.gui.Gui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import smile_libbox.api.event.BarEvents;

/**
 * 1.21.9 起原版新增的上下文条状态选择。低版本编译出的这个类不含任何注入点，
 * 因此不会去找不存在的方法；高版本把原版枚举结果交给 {@link BarEvents}，
 * 使用方无需引用包私有枚举即可替换状态。
 */
@Mixin(Gui.class)
public abstract class LibContextualBarMixin {

	//? if >=1.21.9 {
	/*@Inject(method = "nextContextualInfoState", at = @At("RETURN"), cancellable = true)
	private void libbox$chooseBar(CallbackInfoReturnable<Object> cir) {
		Object vanilla = cir.getReturnValue();
		Object replacement = BarEvents.dispatch(vanilla);
		if (replacement != null && BarEvents.jumpStateAllowed(vanilla, replacement)) {
			cir.setReturnValue(replacement);
		}
	}
	*///?}
}
