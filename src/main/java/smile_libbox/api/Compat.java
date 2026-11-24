package smile_libbox.api;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * 版本能力查询与反射转译基础。
 *
 * <p>这是「单 jar 转译层」的底座：把「某个 ABI 分界之后才存在的接口」统一成固定的
 * 布尔查询，让业务模组不必再写 {@code //? if >=1.21.9} 之类的编译期条件；
 * 同时提供按<b>参数形状</b>而不是方法名查找 / 调用的工具——正式环境里游戏方法名是
 * intermediary 名（{@code method_xxxxx}），按名字写死会在开发环境或跨版本时失效，
 * 而描述符形状在命名空间之间是一致的。</p>
 *
 * <p>本类不引用任何 Minecraft 类，因此可以在所有版本上加载。</p>
 */
public final class Compat {

	private Compat() {
	}

	// ------------------------------------------------------------------
	// 固定能力查询：把 ABI 分界收敛成一组布尔值
	// ------------------------------------------------------------------

	/** 1.20.2 起：{@code renderBackground} 增加窗口参数，滚轮回调增加水平分量。 */
	public static boolean fourArgBackground() {
		return GameVersion.atLeast("1.20.2");
	}

	/** 1.20.5 起：物品栈改用 DataComponent，NBT 读写接口整体替换。 */
	public static boolean dataComponents() {
		return GameVersion.atLeast("1.20.5");
	}

	/** 1.21 起：{@code ResourceLocation} 构造改为静态工厂。 */
	public static boolean resourceLocationFactory() {
		return GameVersion.atLeast("1.21");
	}

	/** 1.21.5 起：讲述人 {@code say} 增加音量参数。 */
	public static boolean narratorVolume() {
		return GameVersion.atLeast("1.21.5");
	}

	/** 1.21.9 起：输入回调改事件对象、键位分类对象化、资源类改名、上下文条出现。 */
	public static boolean eventBasedInput() {
		return GameVersion.atLeast("1.21.9");
	}

	/** 1.21.9 起：快捷栏上方改为经验条 / 定位条 / 跳跃条三选一的上下文条。 */
	public static boolean contextualBar() {
		return eventBasedInput();
	}

	// ------------------------------------------------------------------
	// 反射工具：按描述符形状定位，避开映射命名空间差异
	// ------------------------------------------------------------------

	/**
	 * 在类层次里找「参数个数与返回类型都匹配」的方法。
	 *
	 * @param owner       起始类（会沿父类继续找）
	 * @param paramCount  参数个数
	 * @param returnType  期望返回类型；传 {@code null} 表示不限制
	 * @param nameHints   可选的名字候选（Mojmap / intermediary 名都可），为空则只按形状
	 */
	public static Method findMethod(Class<?> owner, int paramCount, Class<?> returnType,
			String... nameHints) {
		Class<?> current = owner;
		while (current != null && current != Object.class) {
			for (Method method : current.getDeclaredMethods()) {
				if (method.getParameterCount() != paramCount) {
					continue;
				}
				if (returnType != null && method.getReturnType() != returnType) {
					continue;
				}
				if (nameHints.length > 0 && !Arrays.asList(nameHints).contains(method.getName())) {
					continue;
				}
				method.setAccessible(true);
				return method;
			}
			current = current.getSuperclass();
		}
		return null;
	}

	/** 找「参数类型依次可赋值」的方法（用于重载目标明确的场景）。 */
	public static Method findByShape(Class<?> owner, Class<?> returnType, Class<?>... params) {
		Class<?> current = owner;
		while (current != null && current != Object.class) {
			for (Method method : current.getDeclaredMethods()) {
				if (method.getParameterCount() != params.length) {
					continue;
				}
				if (returnType != null && method.getReturnType() != returnType) {
					continue;
				}
				Class<?>[] actual = method.getParameterTypes();
				boolean ok = true;
				for (int i = 0; i < params.length; i++) {
					if (params[i] != null && !boxed(actual[i]).isAssignableFrom(boxed(params[i]))) {
						ok = false;
						break;
					}
				}
				if (ok) {
					method.setAccessible(true);
					return method;
				}
			}
			current = current.getSuperclass();
		}
		return null;
	}

	/** 按参数个数找构造器。 */
	public static Constructor<?> findConstructor(Class<?> owner, int paramCount) {
		for (Constructor<?> ctor : owner.getDeclaredConstructors()) {
			if (ctor.getParameterCount() == paramCount) {
				ctor.setAccessible(true);
				return ctor;
			}
		}
		return null;
	}

	/** 反射调用，异常统一返回 {@code null} 而不是抛出。 */
	public static Object call(Method method, Object target, Object... args) {
		if (method == null) {
			return null;
		}
		try {
			return method.invoke(target, args);
		} catch (Throwable ignored) {
			return null;
		}
	}

	/** 读取一个无参、返回 int 的字段式方法（如 {@code KeyEvent#key()}）。 */
	public static int intOf(Object event, String... nameHints) {
		if (event == null) {
			return Integer.MIN_VALUE;
		}
		Method method = findMethod(event.getClass(), 0, int.class, nameHints);
		Object value = call(method, event);
		return value instanceof Integer ? (Integer) value : Integer.MIN_VALUE;
	}

	/** 读取一个无参、返回 double 的字段式方法（如 {@code MouseButtonEvent#x()}）。 */
	public static double doubleOf(Object event, String... nameHints) {
		if (event == null) {
			return Double.NaN;
		}
		Method method = findMethod(event.getClass(), 0, double.class, nameHints);
		Object value = call(method, event);
		return value instanceof Double ? (Double) value : Double.NaN;
	}

	private static Class<?> boxed(Class<?> type) {
		if (!type.isPrimitive()) {
			return type;
		}
		if (type == int.class) return Integer.class;
		if (type == long.class) return Long.class;
		if (type == double.class) return Double.class;
		if (type == float.class) return Float.class;
		if (type == boolean.class) return Boolean.class;
		if (type == short.class) return Short.class;
		if (type == byte.class) return Byte.class;
		if (type == char.class) return Character.class;
		return Void.class;
	}
}
