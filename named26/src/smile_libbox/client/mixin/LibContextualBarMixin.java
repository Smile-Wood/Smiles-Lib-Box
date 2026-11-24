package smile_libbox.client.mixin;

import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import smile_libbox.api.event.BarEvents;
import smile_libbox.client.compat.Named26Hooks;

/**
 * 26.2（named）的上下文条注入点。
 *
 * <p>1.21.9~1.21.x 时状态枚举挂在 {@code Gui$ContextualInfo}、选择方法是
 * {@code Gui#nextContextualInfoState()}；26.2 把整套搬到了 {@link Hud}，枚举变成
 * {@code Hud$ContextualInfo}（包私有，仍是 EMPTY / EXPERIENCE / LOCATOR /
 * JUMPABLE_VEHICLE 四个，顺序不变）。因此这里必须按 26.2 的类重新编译一份注入点，
 * 不能复用低版本 jar 里的 {@code @Mixin(Gui)}。</p>
 *
 * <p>注入结果只交给纯 Java 的 {@link BarEvents}，使用方 jar 依旧没有 MC 引用。</p>
 */
@Mixin(Hud.class)
public abstract class LibContextualBarMixin {

	@Inject(method = "nextContextualInfoState", at = @At("RETURN"),
			cancellable = true, require = 1)
	private void libbox$chooseBar(CallbackInfoReturnable<Object> cir) {
		Named26Hooks.first("Hud.nextContextualInfoState");
		Object vanilla = cir.getReturnValue();
		Object replacement = BarEvents.dispatch(vanilla);
		if (BarEvents.jumpStateAllowed(vanilla, replacement)) {
			cir.setReturnValue(replacement);
		}
	}
}
