package smile_libbox.api.event;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 版本无关的输入事件总线。
 *
 * <p>库在每个游戏版本里用对应版本的方法签名编译 mixin，把 {@code Screen#keyPressed}、
 * {@code MouseHandler#onPress} / {@code onButton} 这些会变签名的注入点统一转成这里的固定回调。
 * 依赖本库的模组只注册回调，不需要自己写 mixin，因此可以是单 jar。</p>
 *
 * <p>回调参数刻意用 {@link Object} / 基本类型，不出现任何 {@code net.minecraft} 类型：
 * 这样描述符在所有游戏版本里一致，编译期链接不会因为版本变化而断掉。需要访问界面对象时
 * 由使用方自行向下转型（{@code net.minecraft.client.gui.screens.Screen} 是跨版本稳定类）。</p>
 */
public final class InputEvents {

	private InputEvents() {
	}

	/** 界面按键。返回 {@code true} 表示该键已被消费，库会取消原版处理。 */
	@FunctionalInterface
	public interface ScreenKeyHandler {
		boolean onKey(Object screen, int keyCode, int scanCode, int modifiers);
	}

	/**
	 * 鼠标按键。{@code action} 是 GLFW 动作值（按下为 {@code GLFW.GLFW_PRESS}）。
	 * 返回 {@code true} 表示吞掉这次点击。
	 */
	@FunctionalInterface
	public interface MouseButtonHandler {
		boolean onButton(long window, int button, int action);
	}

	private static final List<ScreenKeyHandler> SCREEN_KEYS = new CopyOnWriteArrayList<>();
	private static final List<MouseButtonHandler> MOUSE_BUTTONS = new CopyOnWriteArrayList<>();

	public static void onScreenKey(ScreenKeyHandler handler) {
		if (handler != null && !SCREEN_KEYS.contains(handler)) {
			SCREEN_KEYS.add(handler);
		}
	}

	public static void onMouseButton(MouseButtonHandler handler) {
		if (handler != null && !MOUSE_BUTTONS.contains(handler)) {
			MOUSE_BUTTONS.add(handler);
		}
	}

	/** 由库内部 mixin 调用：任一处理器返回 {@code true} 即认为事件已消费。 */
	public static boolean dispatchScreenKey(Object screen, int keyCode, int scanCode, int modifiers) {
		for (ScreenKeyHandler handler : SCREEN_KEYS) {
			try {
				if (handler.onKey(screen, keyCode, scanCode, modifiers)) {
					return true;
				}
			} catch (Throwable t) {
				// 单个使用方的异常不能拖垮输入分发
				System.err.println("[smile_libbox] InputEvents.onKey 处理器异常: " + t);
			}
		}
		return false;
	}

	/** 由库内部 mixin 调用：任一处理器返回 {@code true} 即吞掉这次点击。 */
	public static boolean dispatchMouseButton(long window, int button, int action) {
		for (MouseButtonHandler handler : MOUSE_BUTTONS) {
			try {
				if (handler.onButton(window, button, action)) {
					return true;
				}
			} catch (Throwable t) {
				System.err.println("[smile_libbox] InputEvents.onButton 处理器异常: " + t);
			}
		}
		return false;
	}
}
