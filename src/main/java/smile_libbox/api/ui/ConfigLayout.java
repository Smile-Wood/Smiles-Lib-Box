package smile_libbox.api.ui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 从 JSON 定义配置界面，并把行映射到 {@link ConfigValues}。
 *
 * <p>这是「JSON 定义」那一种写法，产出与手写 {@link ConfigSpec} 完全相同的数据，
 * 渲染走同一个 {@link ConfigScreen} —— 两种定义方式画出来必然一致。</p>
 *
 * <p>JSON 里所有 {@code title} / {@code label} / {@code tooltip} / {@code button}
 * 都是翻译键，加载时由 {@link Ui#tr(String, Object...)} 翻成当前语言。
 * 结构：</p>
 *
 * <pre>{@code
 * {
 *   "title": "mymod.gui.title",
 *   "pages": [
 *     {
 *       "title": "mymod.gui.tab.general",
 *       "rows": [
 *         { "type": "toggle", "key": "enabled", "label": "mymod.config.enabled",
 *           "tooltip": "mymod.config.enabled.tooltip", "default": true },
 *         { "type": "slider", "key": "rate", "label": "mymod.config.rate",
 *           "min": 50, "max": 200, "default": 100, "requires": "enabled" },
 *         { "type": "slider", "key": "port", "label": "mymod.config.port",
 *           "min": 1, "max": 65535, "default": 25565,
 *           "regex": "[0-9]{1,5}", "regexName": "mymod.config.port.hint" },
 *         { "type": "string", "key": "last_file", "label": "mymod.config.last_file",
 *           "default": "config.json", "regexFile": "json" },
 *         { "type": "text", "label": "mymod.gui.hint" },
 *         { "type": "action", "label": "mymod.gui.voice", "action": "select_voice" }
 *       ]
 *     }
 *   ]
 * }
 * }</pre>
 *
 * <p>{@code action} 行的按钮文字会再次经过 {@link Action#label()} 取一次，
 * 因此音色名这类运行时才知道的内容可以动态变化。</p>
 *
 * <p>{@code "requires"} 引用同一份定义里某个 <b>toggle</b> 行的 {@code key}：前置关掉时
 * 本行禁用（置灰 + 悬停红字提示）。可以出现在前置之前或之后、也可以跨页引用；
 * 找不到前置或前置不是 toggle 时按「没有约束」处理，不报错。</p>
 *
 * <p>根上的 {@code "systemButtons": false} 会把右下角两颗系统按钮（「完成」+「全部重置」）
 * 一并关掉，适合底栏要用自己的按钮填满的界面；只想关掉其中一颗就用 {@code "showDone"} /
 * {@code "showReset"}，{@code "doneLabel"} 用来换掉「完成」的文字。</p>
 *
 * <p>输入框（{@code slider} 的数值框、{@code string} 行的文本框）的校验文案由三个可选
 * 字段控制：{@code "regex"} 是整串匹配的正则，
 * {@code "regexName"} 是它的翻译键（出错提示写成「不合法的字符串！仅支持{名字}」），
 * 或者用 {@code "regexFile": "txt"} 一步到位——只接受 {@code .txt} 结尾，文案自动是
 * 「.txt格式文件」。{@code string} 行不写正则就什么都收；数值越界则固定用
 * 「不合法的数字！仅支持{min}~{max}之间的数据」。</p>
 */
public final class ConfigLayout {

	/** action 行的行为：按钮文字 + 点击动作。 */
	public interface Action {
		/** 已经翻译好的按钮文字，每次绘制都会调用。 */
		String label();

		void run();
	}

	private ConfigLayout() {
	}

	/**
	 * 解析 JSON。
	 *
	 * @param json 定义文本
	 * @param parent 父界面句柄，可为 {@code null}
	 * @param values 值的读写口子
	 * @param actions {@code action} 行引用的行为表；没有 action 行时传空表即可
	 * @return 可直接交给 {@link Ui#config(Object, ConfigSpec)} 的描述
	 */
	public static ConfigSpec parse(String json, Object parent, ConfigValues values,
			Map<String, Action> actions) {
		JsonObject root = JsonParser.parseString(json).getAsJsonObject();
		ConfigSpec spec = new ConfigSpec(
				tr(string(root, "title", "")), parent, values == null ? null : values::save);

		// 系统按钮（右下角「完成」+「全部重置」）可按需关掉：systemButtons 是总开关，
		// 后面两个字段单独覆盖其中一颗，写在同一份定义里时以更具体的为准。
		if (root.has("systemButtons")) {
			spec.showSystemButtons(bool(root, "systemButtons", true));
		}
		if (root.has("showDone")) {
			spec.showDone = bool(root, "showDone", true);
		}
		if (root.has("showReset")) {
			spec.showReset = bool(root, "showReset", true);
		}
		String doneLabel = string(root, "doneLabel", null);
		if (doneLabel != null && !doneLabel.isEmpty()) {
			spec.doneLabel = tr(doneLabel);
		}

		// 前置项按 key 引用，可能出现在任意页/任意位置，所以先建全部行再统一接线
		Map<String, ConfigSpec.Row> byKey = new LinkedHashMap<>();
		java.util.List<ConfigSpec.Row> dependent = new java.util.ArrayList<>();
		java.util.List<String> dependentOn = new java.util.ArrayList<>();

		JsonArray pages = array(root, "pages");
		if (pages == null) {
			return spec;
		}
		for (JsonElement element : pages) {
			if (!element.isJsonObject()) {
				continue;
			}
			JsonObject pageObject = element.getAsJsonObject();
			ConfigSpec.Page page = spec.page(tr(string(pageObject, "title", "")));
			JsonArray rows = array(pageObject, "rows");
			if (rows == null) {
				continue;
			}
			for (JsonElement rowElement : rows) {
				if (!rowElement.isJsonObject()) {
					continue;
				}
				JsonObject rowObject = rowElement.getAsJsonObject();
				addRow(page, rowObject, values, actions);
				ConfigSpec.Row created = page.last();
				if (created == null) {
					continue;
				}
				if (created.key != null) {
					byKey.putIfAbsent(created.key, created);
				}
				String requires = string(rowObject, "requires", null);
				if (requires != null && !requires.isEmpty()) {
					dependent.add(created);
					dependentOn.add(requires);
				}
			}
		}
		for (int i = 0; i < dependent.size(); i++) {
			ConfigSpec.Row required = byKey.get(dependentOn.get(i));
			// 找不到前置、或前置不是开关行：当作没有约束，不抛异常，界面照常开
			if (required != null) {
				dependent.get(i).requires(required);
			}
		}
		return spec;
	}

	/** 从资源目录读一份 JSON 再解析；资源不存在时返回 {@code null}。 */
	public static ConfigSpec load(String resourcePath, Object parent, ConfigValues values,
			Map<String, Action> actions) {
		String json = Resources.read(resourcePath);
		return json == null ? null : parse(json, parent, values, actions);
	}

	private static void addRow(ConfigSpec.Page page, JsonObject row, ConfigValues values,
			Map<String, Action> actions) {
		String type = string(row, "type", "text");
		String label = tr(string(row, "label", ""));
		String tooltip = tr(string(row, "tooltip", ""));
		String key = string(row, "key", null);

		switch (type) {
			case "toggle": {
				boolean fallback = bool(row, "default", false);
				boolean current = values == null ? fallback : values.getBoolean(key, fallback);
				page.toggle(key, label, tooltip, current, fallback,
						values == null ? null : value -> values.setBoolean(key, value));
				break;
			}
			case "slider": {
				int min = number(row, "min", 0);
				int max = number(row, "max", 100);
				int fallback = clamp(number(row, "default", min), min, max);
				int current = values == null ? fallback : clamp(values.getInt(key, fallback), min, max);
				page.slider(key, label, tooltip, current, min, max, fallback,
						values == null ? null : value -> values.setInt(key, value));
				// 可选：数字输入框的合法性正则，缺省用 ConfigSpec.Row 的整数默认正则
				applyRegex(page.last(), row);
				// 可选：只要输入框，不画滑条（"boxOnly": true）
				if (bool(row, "boxOnly", false)) {
					page.last().boxOnly();
				}
				break;
			}
			case "number": {
				// 与 "slider" 取值规则相同，只是不画滑条、控件列整条给输入框
				int min = number(row, "min", 0);
				int max = number(row, "max", 100);
				int fallback = clamp(number(row, "default", min), min, max);
				int current = values == null ? fallback : clamp(values.getInt(key, fallback), min, max);
				page.number(key, label, tooltip, current, min, max, fallback,
						values == null ? null : value -> values.setInt(key, value));
				applyRegex(page.last(), row);
				break;
			}
			case "string": {
				String fallback = string(row, "default", "");
				String current = values == null ? fallback : values.getString(key, fallback);
				page.string(key, label, tooltip, current, fallback,
						values == null ? null : value -> values.setString(key, value));
				applyRegex(page.last(), row);
				break;
			}
			case "action": {
				String id = string(row, "action", null);
				Action action = actions == null || id == null ? null : actions.get(id);
				page.action(key, label, tooltip,
						action == null ? () -> tr(string(row, "button", "…")) : action::label,
						action == null ? () -> { } : action::run);
				break;
			}
			default:
				page.text(label);
				break;
		}
	}

	// ------------------------------------------------------------------ JSON 取值

	/**
	 * 可选的校验正则：{@code "regex"} 是整串匹配的正则，{@code "regexName"} 是它的人话名字，
	 * {@code "regexFile": "txt"} 一步到位（只收 {@code .txt} 结尾，文案自动是「.txt格式文件」）。
	 */
	private static void applyRegex(ConfigSpec.Row target, JsonObject definition) {
		if (target == null) {
			return;
		}
		if (definition.has("regex")) {
			target.regex = string(definition, "regex", null);
		}
		if (definition.has("regexName")) {
			target.regexLabel = tr(string(definition, "regexName", null));
		}
		String fileExt = string(definition, "regexFile", null);
		if (fileExt != null && !fileExt.isEmpty()) {
			target.regexFile(fileExt);
		}
	}

	private static String string(JsonObject object, String name, String fallback) {
		JsonElement element = object.get(name);
		return element == null || element.isJsonNull() || !element.isJsonPrimitive()
				? fallback : element.getAsString();
	}

	private static boolean bool(JsonObject object, String name, boolean fallback) {
		JsonElement element = object.get(name);
		try {
			return element == null || element.isJsonNull() ? fallback : element.getAsBoolean();
		} catch (RuntimeException e) {
			return fallback;
		}
	}

	private static int number(JsonObject object, String name, int fallback) {
		JsonElement element = object.get(name);
		try {
			return element == null || element.isJsonNull() ? fallback : element.getAsInt();
		} catch (RuntimeException e) {
			return fallback;
		}
	}

	private static JsonArray array(JsonObject object, String name) {
		JsonElement element = object.get(name);
		return element != null && element.isJsonArray() ? element.getAsJsonArray() : null;
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}

	/** 翻译：键不存在时原样返回，避免界面上出现空白标签。 */
	private static String tr(String key) {
		if (key == null || key.isEmpty()) {
			return "";
		}
		String text = Ui.tr(key);
		return text == null || text.isEmpty() ? key : text;
	}

	/** 便于外部拼装 action 表的小工具。 */
	public static Map<String, Action> actions() {
		return new LinkedHashMap<>();
	}

	/** 资源读取：只依赖 classloader，不碰 MC，因此使用方的 jar 可以零 MC 引用。 */
	static final class Resources {
		private Resources() {
		}

		static String read(String path) {
			if (path == null || path.isEmpty()) {
				return null;
			}
			String normalized = path.startsWith("/") ? path.substring(1) : path;
			ClassLoader loader = ConfigLayout.class.getClassLoader();
			try (java.io.InputStream in = loader.getResourceAsStream(normalized)) {
				if (in == null) {
					return null;
				}
				return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
			} catch (java.io.IOException e) {
				return null;
			}
		}
	}
}
