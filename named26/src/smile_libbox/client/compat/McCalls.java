package smile_libbox.client.compat;

import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;

/**
 * 26.2 官方名空间下的输入注入：这一代全部使用事件对象，没有 1.20.x 的
 * {@code mouseClicked(double,double,int)} / {@code keyPressed(int,int,int)} 重载。
 *
 * <p>与 Stonecutter 版的同名类保持完全一致的静态签名，{@link DebugBackend} 两份源码
 * 因此只在数据层有差异，输入层不用分叉。</p>
 */
public final class McCalls {

	private McCalls() {
	}

	private static MouseButtonEvent event(double x, double y, int button) {
		return new MouseButtonEvent(x, y, new MouseButtonInfo(button, 0));
	}

	public static boolean buttonClicked(Object widget, double mouseX, double mouseY, int button) {
		return ((GuiEventListener) widget).mouseClicked(event(mouseX, mouseY, button), false);
	}

	public static boolean buttonReleased(Object widget, double mouseX, double mouseY, int button) {
		return ((GuiEventListener) widget).mouseReleased(event(mouseX, mouseY, button));
	}

	public static boolean mouseScrolled(Object widget, double mouseX, double mouseY,
			double deltaY, double deltaX) {
		return ((GuiEventListener) widget).mouseScrolled(mouseX, mouseY, deltaX, deltaY);
	}

	public static boolean keyPressed(Object screen, int keyCode, int scanCode, int modifiers) {
		return ((GuiEventListener) screen).keyPressed(new KeyEvent(keyCode, scanCode, modifiers));
	}

	public static boolean keyReleased(Object screen, int keyCode, int scanCode, int modifiers) {
		return ((GuiEventListener) screen).keyReleased(new KeyEvent(keyCode, scanCode, modifiers));
	}

	/** 26.2 的 {@code CharacterEvent} 只带码点，修饰符改由事件上下文提供。 */
	public static boolean charTyped(Object screen, int codepoint, int modifiers) {
		return ((GuiEventListener) screen).charTyped(new CharacterEvent(codepoint));
	}
}
