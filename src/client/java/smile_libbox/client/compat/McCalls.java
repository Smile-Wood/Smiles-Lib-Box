package smile_libbox.client.compat;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.events.GuiEventListener;

/**
 * 几个零散但会变签名的 MC 调用，统一收在库里按版本编译。
 *
 * <p>使用方只看到这几个固定静态方法，单 jar 里不需要出现任何
 * {@code KeyMapping.Category} / {@code Window#handle} / {@code MouseButtonEvent} 之类的
 * 版本相关符号。</p>
 */
public final class McCalls {

	private McCalls() {
	}

	/** 注册键位：1.21.9 起分类参数从字符串变成 {@code KeyMapping.Category}。 */
	public static KeyMapping keyMapping(String namespace, String translationKey, int keyCode) {
		//? if >=1.21.9 {
		/*return new KeyMapping(translationKey, InputConstants.Type.KEYSYM, keyCode, category(namespace));
		*///?} else {
		return new KeyMapping(translationKey, InputConstants.Type.KEYSYM, keyCode,
				"category." + namespace);
		//?}
	}

	//? if >=1.21.9 {
	/*private static KeyMapping.Category category(String namespace) {
		try {
			return KeyMapping.Category.register(
					net.minecraft.resources.Identifier.fromNamespaceAndPath(namespace, "main"));
		} catch (Throwable t) {
			// 重复注册等情况退回原版「杂项」分类，不让键位注册失败
			return KeyMapping.Category.MISC;
		}
	}
	*///?}

	/** GLFW 窗口句柄：1.21.9 起 {@code getWindow()} 换成 {@code handle()}。 */
	public static long windowHandle(Minecraft client) {
		//? if >=1.21.9 {
		/*return client.getWindow().handle();
		*///?} else {
		return client.getWindow().getWindow();
		//?}
	}

	/** 把一次点击转发给控件：1.21.9 起事件被打包成对象。 */
	public static boolean buttonClicked(Object widget, double mouseX, double mouseY, int button) {
		//? if >=1.21.9 {
		/*return ((GuiEventListener) widget).mouseClicked(
				new net.minecraft.client.input.MouseButtonEvent(mouseX, mouseY,
						new net.minecraft.client.input.MouseButtonInfo(button, 0)), false);
		*///?} else {
		return ((GuiEventListener) widget).mouseClicked(mouseX, mouseY, button);
		//?}
	}

	/** 松开鼠标键：1.21.9 起同样改成事件对象。 */
	public static boolean buttonReleased(Object widget, double mouseX, double mouseY, int button) {
		//? if >=1.21.9 {
		/*return ((GuiEventListener) widget).mouseReleased(
				new net.minecraft.client.input.MouseButtonEvent(mouseX, mouseY,
						new net.minecraft.client.input.MouseButtonInfo(button, 0)));
		*///?} else {
		return ((GuiEventListener) widget).mouseReleased(mouseX, mouseY, button);
		//?}
	}

	/** 滚轮：1.20.2 起增加水平分量，1.21.9 起事件对象化。 */
	public static boolean mouseScrolled(Object widget, double mouseX, double mouseY,
			double deltaY, double deltaX) {
		//? if >=1.21.9 {
		/*return ((GuiEventListener) widget).mouseScrolled(mouseX, mouseY, deltaX, deltaY);
		*///?} else {
		//? if >=1.20.2 {
		/*return ((GuiEventListener) widget).mouseScrolled(mouseX, mouseY, deltaX, deltaY);
		*///?} else {
		return ((GuiEventListener) widget).mouseScrolled(mouseX, mouseY, deltaY);
		//?}
		//?}
	}

	/** 界面按键：1.21.9 起 {@code keyPressed(int,int,int)} 变成 {@code keyPressed(KeyEvent)}。 */
	public static boolean keyPressed(Object screen, int keyCode, int scanCode, int modifiers) {
		//? if >=1.21.9 {
		/*return ((GuiEventListener) screen).keyPressed(
				new net.minecraft.client.input.KeyEvent(keyCode, scanCode, modifiers));
		*///?} else {
		return ((GuiEventListener) screen).keyPressed(keyCode, scanCode, modifiers);
		//?}
	}

	public static boolean keyReleased(Object screen, int keyCode, int scanCode, int modifiers) {
		//? if >=1.21.9 {
		/*return ((GuiEventListener) screen).keyReleased(
				new net.minecraft.client.input.KeyEvent(keyCode, scanCode, modifiers));
		*///?} else {
		return ((GuiEventListener) screen).keyReleased(keyCode, scanCode, modifiers);
		//?}
	}

	/** 逐字符输入：1.21.9 起 {@code charTyped(char,int)} 变成 {@code charTyped(CharacterEvent)}。 */
	public static boolean charTyped(Object screen, int codepoint, int modifiers) {
		//? if >=1.21.9 {
		/*return ((GuiEventListener) screen).charTyped(
				new net.minecraft.client.input.CharacterEvent(codepoint, modifiers));
		*///?} else {
		return ((GuiEventListener) screen).charTyped((char) codepoint, modifiers);
		//?}
	}
}
