package smile_libbox.api.event;

import smile_libbox.api.ui.Ui;

/**
 * 快捷栏上方「上下文条」的版本无关钩子。
 *
 * <p>1.21.9 起原版新增 {@code Gui#nextContextualInfoState()}，低版本没有这个方法，
 * 而它返回的是包私有枚举。库在每个版本里按该版本的描述符编译注入点，把原版结果
 * 交给这里注册的处理器；使用方只需要返回同类型的另一个枚举常量（或 {@code null}
 * 表示不干预），完全不必引用版本相关类型。</p>
 */
public final class BarEvents {

	private BarEvents() {
	}

	@FunctionalInterface
	public interface ContextualInfoHandler {
		/**
		 * @param vanilla 原版算出的状态枚举实例
		 * @return 要替换成的枚举实例；{@code null} 表示保持原版结果
		 */
		Object choose(Object vanilla);
	}

	private static volatile ContextualInfoHandler handler;

	public static void onContextualInfo(ContextualInfoHandler value) {
		handler = value;
	}

	/** 由库内部 mixin 调用。没有处理器或处理器放弃时返回 {@code null}。 */
	public static Object dispatch(Object vanilla) {
		ContextualInfoHandler current = handler;
		if (current == null) {
			return null;
		}
		try {
			return current.choose(vanilla);
		} catch (Throwable t) {
			System.err.println("[smile_libbox] BarEvents 处理器异常: " + t);
			return null;
		}
	}

	/**
	 * 跳跃蓄力条状态的<b>安全闸门</b>，mixin 在写回替换值之前必须过一遍。
	 *
	 * <p>1.21.9 起（26.2 更明显）原版的 {@code nextContextualInfoState()} 只在
	 * {@code LocalPlayer.jumpableVehicle() != null} 时返回 JUMPABLE_VEHICLE。
	 * 一旦把它切到这个状态，{@code Hud} 就会 {@code new JumpableVehicleBar(minecraft)}，
	 * 而它的构造函数是 {@code Objects.requireNonNull(player.jumpableVehicle())}。
	 * 椅子、船、矿车这类「有载具但不可跳跃」的实体在这里是 {@code null}，
	 * 直接抛 NPE → Render Frame 崩溃。</p>
	 *
	 * <p>因此：只有「原版自己也判定该显示」<b>且</b> {@link Ui#hasJumpableVehicle()}
	 * 为真时才允许把状态换成跳跃条。别的替换值（经验条 / 定位条 / 空条）不受影响。</p>
	 *
	 * @param vanilla 原版算出的状态
	 * @param replacement 使用方想替换成的状态
	 * @return 是否允许写回
	 */
	public static boolean jumpStateAllowed(Object vanilla, Object replacement) {
		if (replacement == null) {
			return false;
		}
		Object[] states = Ui.contextualStates();
		// 低版本没有这套枚举，也就没有 JumpableVehicleBar 可崩
		if (states == null || states.length <= 3) {
			return true;
		}
		Object jumpState = states[3];
		if (replacement != jumpState) {
			return true;
		}
		return vanilla == jumpState && Ui.hasJumpableVehicle();
	}
}
