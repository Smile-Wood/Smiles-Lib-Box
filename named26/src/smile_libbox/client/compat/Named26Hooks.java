package smile_libbox.client.compat;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * named26 注入点的一次性日志。
 *
 * <p>mixin 回调每帧 / 每次按键都会跑，日志只能打一次；用独立 holder 而不是往目标类
 * （Hud / Screen / MouseHandler）里塞字段，避免污染原版类。</p>
 */
public final class Named26Hooks {

	private static final Set<String> SEEN = ConcurrentHashMap.newKeySet();

	private Named26Hooks() {
	}

	public static void first(String hook) {
		if (SEEN.add(hook)) {
			System.out.println("[smile_libbox] 26.2 hook active: " + hook);
		}
	}
}
