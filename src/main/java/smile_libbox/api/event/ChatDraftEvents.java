package smile_libbox.api.event;

/**
 * 聊天框草稿的版本无关事件总线。
 *
 * <p>聊天框输入框是 {@code EditBox}（MC 类型），且 {@code ChatScreen#init} /
 * {@code removed} / {@code handleChatInput} 的签名随版本变化，因此这些注入点只能由库
 * 按版本各编译一份。使用方不认识 {@code EditBox}，只在这里注册两个纯 Java 回调：</p>
 *
 * <ul>
 *   <li>{@link #onCapture(Capture)}：聊天框关闭 / 消息提交时收到「连接地址 + 输入框文本」，
 *       由使用方决定要不要持久化、按什么 key、保留多久。文本为空串表示应当清除草稿。</li>
 *   <li>{@link #onRestore(Restore)}：聊天框初始化时按连接地址取回草稿，返回 {@code null}
 *       或空串表示不干涉原版（原版 26.2 自带会话内草稿，优先级更高）。</li>
 * </ul>
 *
 * <p>地址由 {@code Ui.serverAddress()} 提供，是玩家填写的服务器地址（{@code ServerData.ip}），
 * 不是玩家名、世界名或客户端实例 —— 这样换客户端 / 顶号重启后仍命中同一条草稿。</p>
 */
public final class ChatDraftEvents {

	private ChatDraftEvents() {
	}

	@FunctionalInterface
	public interface Capture {
		void accept(String address, String text);
	}

	@FunctionalInterface
	public interface Restore {
		String get(String address);
	}

	private static volatile Capture capture;
	private static volatile Restore restore;

	public static void onCapture(Capture handler) {
		capture = handler;
	}

	public static void onRestore(Restore handler) {
		restore = handler;
	}

	public static boolean ready() {
		return capture != null && restore != null;
	}

	public static String describe() {
		return "捕获=" + (capture == null ? "未注册" : "已注册")
				+ "，恢复=" + (restore == null ? "未注册" : "已注册");
	}

	/** 由库内部 mixin 调用。单个使用方的异常绝不能让聊天框关闭流程崩溃。 */
	public static void dispatchCapture(String address, String text) {
		Capture current = capture;
		if (current == null) {
			return;
		}
		try {
			current.accept(address == null ? "" : address, text == null ? "" : text);
		} catch (Throwable t) {
			System.err.println("[smile_libbox] ChatDraftEvents.capture 处理器异常: " + t);
		}
	}

	/** 由库内部 mixin 调用。没有处理器或处理器抛异常时返回 {@code null}。 */
	public static String dispatchRestore(String address) {
		Restore current = restore;
		if (current == null) {
			return null;
		}
		try {
			return current.get(address == null ? "" : address);
		} catch (Throwable t) {
			System.err.println("[smile_libbox] ChatDraftEvents.restore 处理器异常: " + t);
			return null;
		}
	}
}
