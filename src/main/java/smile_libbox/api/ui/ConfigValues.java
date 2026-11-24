package smile_libbox.api.ui;

/**
 * 配置值的读写口子。
 *
 * <p>JSON 定义的界面只知道「键名」，不关心值存在哪：使用方把键名映射到自己的
 * 配置文件字段即可。界面渲染与值存储因此解耦 —— 换一个存法不用动界面定义。</p>
 */
public interface ConfigValues {

	boolean getBoolean(String key, boolean defaultValue);

	void setBoolean(String key, boolean value);

	int getInt(String key, int defaultValue);

	void setInt(String key, int value);

	default String getString(String key, String defaultValue) {
		return defaultValue;
	}

	default void setString(String key, String value) {
	}

	/** 值全部写完后的落盘钩子。界面点「完成」或关闭时调用。 */
	default void save() {
	}

	/** 一个只存在于内存的默认实现，用来做预览与自检。 */
	static ConfigValues memory() {
		return new ConfigValues() {
			private final java.util.Map<String, Boolean> booleans = new java.util.HashMap<>();
			private final java.util.Map<String, Integer> ints = new java.util.HashMap<>();
			private final java.util.Map<String, String> strings = new java.util.HashMap<>();

			@Override
			public boolean getBoolean(String key, boolean defaultValue) {
				return this.booleans.getOrDefault(key, defaultValue);
			}

			@Override
			public void setBoolean(String key, boolean value) {
				this.booleans.put(key, value);
			}

			@Override
			public int getInt(String key, int defaultValue) {
				return this.ints.getOrDefault(key, defaultValue);
			}

			@Override
			public void setInt(String key, int value) {
				this.ints.put(key, value);
			}

			@Override
			public String getString(String key, String defaultValue) {
				return this.strings.getOrDefault(key, defaultValue);
			}

			@Override
			public void setString(String key, String value) {
				this.strings.put(key, value);
			}
		};
	}
}
