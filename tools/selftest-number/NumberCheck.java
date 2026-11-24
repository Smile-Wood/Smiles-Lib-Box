import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;
import smile_libbox.api.ui.ConfigLayout;
import smile_libbox.api.ui.ConfigSpec;
import smile_libbox.api.ui.ConfigValues;

/** 脱离 Minecraft 的纯 Java 回归：数字输入框实时校验 + 两种定义路线的一致性。 */
public final class NumberCheck {

	private static int passed;
	private static int failed;

	private static void expect(String label, String actual, String expected) {
		boolean ok = expected == null ? actual == null : expected.equals(actual);
		if (ok) {
			passed++;
		} else {
			failed++;
			System.out.println("  FAIL " + label + " -> " + actual + "（期望 " + expected + "）");
		}
	}

	private static void expectEquals(String label, Object actual, Object expected) {
		boolean ok = expected == null ? actual == null : expected.equals(actual);
		if (ok) {
			passed++;
		} else {
			failed++;
			System.out.println("  FAIL " + label + " -> " + actual + "（期望 " + expected + "）");
		}
	}

	private static ConfigSpec.Row codeRow() {
		ConfigSpec spec = new ConfigSpec("title", null, null);
		spec.page("page").slider("count", "Count", "tip", 5, 1, 50, 5, v -> { });
		return spec.pages.get(0).rows.get(0);
	}

	private static void defaultRegexChecks(ConfigSpec.Row row, String label) {
		System.out.println("[" + label + "] 默认正则 -?[0-9]+，min=1 max=50");
		expect("null", row.numberError(null), "empty");
		expect("空串", row.numberError(""), "empty");
		expect("全空格", row.numberError("   "), "empty");
		expect("字母", row.numberError("abc"), "pattern");
		expect("数字夹字母", row.numberError("12a"), "pattern");
		expect("正号", row.numberError("+5"), "pattern");
		expect("小数", row.numberError("1.5"), "pattern");
		expect("合法 5", row.numberError("5"), null);
		expect("下边界 1", row.numberError("1"), null);
		expect("上边界 50", row.numberError("50"), null);
		expect("首尾空格", row.numberError("  7  "), null);
		expect("负数 -5", row.numberError("-5"), "range_error");
		expect("0 越下界", row.numberError("0"), "range_error");
		expect("51 越上界", row.numberError("51"), "range_error");
		expect("9999 越上界", row.numberError("9999"), "range_error");
	}

	public static void main(String[] args) {
		// ---------- 路线一：代码逐条定义 ----------
		ConfigSpec.Row code = codeRow();
		defaultRegexChecks(code, "代码定义");

		System.out.println("[代码定义] 自定义正则 / 非法正则兜底");
		code.regex("[0-9]{1,2}");
		expect("自定义正则：负数被拒", code.numberError("-5"), "pattern");
		expect("自定义正则：三位被拒", code.numberError("123"), "pattern");
		expect("自定义正则：两位合法", code.numberError("42"), null);
		code.regex("[0-9]+");
		expect("自定义正则：超范围仍拦", code.numberError("999"), "range_error");
		code.regex("[");
		expect("非法正则：字母落到整数解析", code.numberError("abc"), "pattern");
		expect("非法正则：合法值仍通过", code.numberError("9"), null);
		expect("非法正则：超大整数溢出按越界报", code.numberError("99999999999999"), "range_error");

		// ---------- 正则的显示名（错误文案模板） ----------
		System.out.println("[错误文案] 正则名字");
		expectEquals("没写名字 → 内置整数规则", codeRow().regexName(),
				"smile_libbox.gui.regex.integer");
		ConfigSpec.Row named = codeRow().regex("[A-Za-z]:.*", "本地文件夹");
		expectEquals("自定义名字", named.regexName(), "本地文件夹");
		expect("自定义名字：不匹配仍归字符串不合法", named.numberError("???"), "pattern");

		ConfigSpec.Row file = codeRow().regexFile(".txt");
		expectEquals("文件正则：.txt 被接受", Pattern.matches(file.regex, "音色.txt"),
				Boolean.TRUE);
		expectEquals("文件正则：后缀大小写不敏感",
				Pattern.matches(file.regex, "音色.TXT"), Boolean.TRUE);
		expectEquals("文件正则：别的后缀被拒", Pattern.matches(file.regex, "音色.png"),
				Boolean.FALSE);
		expectEquals("文件正则：名字走语言键", file.regexName(),
				"smile_libbox.gui.regex.file");

		// ---------- 路线二：JSON 定义 ----------
		String json = "{"
				+ "\"title\":\"t\",\"pages\":[{\"title\":\"p\",\"rows\":["
				+ "{\"type\":\"slider\",\"key\":\"count\",\"label\":\"L\","
				+ " \"min\":1,\"max\":50,\"default\":5,\"regex\":\"[0-9]{1,2}\"},"
				+ "{\"type\":\"slider\",\"key\":\"plain\",\"label\":\"P\","
				+ " \"min\":1,\"max\":50,\"default\":5},"
				+ "{\"type\":\"slider\",\"key\":\"named\",\"label\":\"N\","
				+ " \"min\":1,\"max\":50,\"default\":5,"
				+ " \"regex\":\"[A-Za-z]:.*\",\"regexName\":\"本地文件夹\"},"
				+ "{\"type\":\"slider\",\"key\":\"file\",\"label\":\"F\","
				+ " \"min\":1,\"max\":50,\"default\":5,\"regexFile\":\"txt\"}"
				+ "]}]}";
		Map<String, Integer> store = new LinkedHashMap<>();
		ConfigValues values = new ConfigValues() {
			public boolean getBoolean(String key, boolean d) { return d; }
			public void setBoolean(String key, boolean v) { }
			public int getInt(String key, int d) { return store.getOrDefault(key, d); }
			public void setInt(String key, int v) { store.put(key, v); }
		};
		ConfigSpec parsed = ConfigLayout.parse(json, null, values, null);
		ConfigSpec.Row jsonRow = parsed.pages.get(0).rows.get(0);
		ConfigSpec.Row plainRow = parsed.pages.get(0).rows.get(1);
		ConfigSpec.Row jsonNamed = parsed.pages.get(0).rows.get(2);
		ConfigSpec.Row jsonFile = parsed.pages.get(0).rows.get(3);

		System.out.println("[JSON 定义] 字段映射与 regex");
		expectEquals("带 regex 行的 key", jsonRow.key, "count");
		expectEquals("min", jsonRow.min, 1);
		expectEquals("max", jsonRow.max, 50);
		expectEquals("默认正则", plainRow.regex, ConfigSpec.Row.DEFAULT_NUMBER_REGEX);
		expectEquals("自定义 regex", jsonRow.regex, "[0-9]{1,2}");
		expectEquals("regexName 字段", jsonNamed.regexName(), "本地文件夹");
		expectEquals("regexFile 字段的名字", jsonFile.regexName(),
				"smile_libbox.gui.regex.file");
		expectEquals("regexFile 字段的正则", Pattern.matches(jsonFile.regex, "a.txt"),
				Boolean.TRUE);
		expectEquals("regexFile 字段拒绝别的后缀",
				Pattern.matches(jsonFile.regex, "a.png"), Boolean.FALSE);
		expect("JSON 自定义正则：负数被拒", jsonRow.numberError("-1"), "pattern");
		expect("JSON 自定义正则：合法", jsonRow.numberError("9"), null);
		expect("JSON 默认正则：负数可过正则但越界", plainRow.numberError("-1"), "range_error");
		expect("JSON 默认正则：超范围", plainRow.numberError("9999"), "range_error");
		expect("JSON 默认正则：合法", plainRow.numberError("5"), null);
		jsonRow.intSave.accept(17);
		expectEquals("JSON 值写回使用方配置", store.get("count"), 17);

		// ---------- 两条路线产出结构一致 ----------
		System.out.println("[一致性] 两条路线产出的 Row 结构");
		ConfigSpec.Row baseline = codeRow();
		expectEquals("kind", plainRow.kind, baseline.kind);
		expectEquals("min", plainRow.min, baseline.min);
		expectEquals("max", plainRow.max, baseline.max);
		expectEquals("intDefault", plainRow.intDefault, baseline.intDefault);
		expectEquals("numberError(5)", plainRow.numberError("5"), baseline.numberError("5"));
		expectEquals("numberError(9999)", plainRow.numberError("9999"), baseline.numberError("9999"));

		// ---------- 前置项（requires）----------
		System.out.println("[前置项] 代码定义");
		ConfigSpec linked = new ConfigSpec("t", null, null);
		ConfigSpec.Page linkedPage = linked.page("p");
		linkedPage.toggle("master", "Master", null, true, true, v -> { });
		ConfigSpec.Row master = linkedPage.last();
		linkedPage.slider("child", "Child", null, 5, 1, 50, 5, v -> { });
		ConfigSpec.Row child = linkedPage.last().requires(master);

		expectEquals("初始：前置开 → 从属可用", child.available(), Boolean.TRUE);
		master.booleanValue = false;
		expectEquals("前置关 → 从属禁用", child.available(), Boolean.FALSE);
		expectEquals("前置自身永远可用", master.available(), Boolean.TRUE);
		master.booleanValue = true;
		expectEquals("前置重开 → 从属恢复", child.available(), Boolean.TRUE);

		linkedPage.slider("other", "Other", null, 5, 1, 50, 5, v -> { });
		ConfigSpec.Row notToggle = linkedPage.last();
		child.requires(notToggle);
		expectEquals("前置不是开关 → 忽略", child.requires, master);

		linkedPage.slider("child2", "Child 2", null, 5, 1, 50, 5, v -> { });
		ConfigSpec.Row child2 = linkedPage.last().requires(master);
		master.booleanValue = false;
		expectEquals("一对多：第二个从属也禁用", child2.available(), Boolean.FALSE);
		expectEquals("一对多：两个从属指向同一前置", child2.requires, master);
		expectEquals("一对多：第一个从属仍是同一前置", child.requires, master);

		System.out.println("[前置项] JSON 定义");
		String linkedJson = "{\"title\":\"t\",\"pages\":[{\"title\":\"p\",\"rows\":["
				+ "{\"type\":\"slider\",\"key\":\"child\",\"label\":\"C\","
				+ " \"min\":1,\"max\":10,\"default\":5,\"requires\":\"master\"},"
				+ "{\"type\":\"toggle\",\"key\":\"master\",\"label\":\"M\",\"default\":true}"
				+ "]}]}";
		ConfigSpec jsonLinked = ConfigLayout.parse(linkedJson, null, null, null);
		ConfigSpec.Row jsonChild = jsonLinked.pages.get(0).rows.get(0);
		ConfigSpec.Row jsonMaster = jsonLinked.pages.get(0).rows.get(1);
		expectEquals("JSON：前置写在后面也能接线", jsonChild.requires, jsonMaster);
		expectEquals("JSON：前置开时可用", jsonChild.available(), Boolean.TRUE);
		jsonMaster.booleanValue = false;
		expectEquals("JSON：前置关时禁用", jsonChild.available(), Boolean.FALSE);
		jsonMaster.booleanValue = true;

		String danglingJson = "{\"title\":\"t\",\"pages\":[{\"title\":\"p\",\"rows\":["
				+ "{\"type\":\"slider\",\"key\":\"child\",\"label\":\"C\","
				+ " \"min\":1,\"max\":10,\"default\":5,\"requires\":\"nope\"}"
				+ "]}]}";
		ConfigSpec dangling = ConfigLayout.parse(danglingJson, null, null, null);
		expectEquals("JSON：前置 key 不存在 → 不约束",
				dangling.pages.get(0).rows.get(0).requires, null);

		// ---------- 底部那一行 ----------
		System.out.println("[底部行] 自动宽度 / 禁用提示 / 对齐");
		ConfigSpec bottomSpec = new ConfigSpec("t", null, null);
		ConfigSpec.Page bottomPage = bottomSpec.page("p");
		bottomPage.bottom.button(() -> "召唤", () -> true, () -> { });
		ConfigSpec.Item spawnItem = bottomPage.bottom.last();
		bottomPage.bottom.text(() -> "进行中", 0xFFFF5555);
		bottomPage.bottom.link(() -> "文档", "https://example.com");

		expectEquals("三项都记下了", bottomPage.bottom.items.size(), 3);
		expectEquals("按钮恒可用", spawnItem.enabled(), Boolean.TRUE);
		expectEquals("没配禁用提示 → 内置文案", spawnItem.disabledTipText(),
				"smile_libbox.gui.unavailable");
		spawnItem.disabledTip("先登录");
		expectEquals("配了禁用提示就用它", spawnItem.disabledTipText(), "先登录");
		expectEquals("文字项内容", bottomPage.bottom.items.get(1).label(), "进行中");
		expectEquals("文字项颜色", bottomPage.bottom.items.get(1).argb, 0xFFFF5555);
		expectEquals("自动宽度的最小宽度", ConfigSpec.Bottom.MIN_BUTTON_WIDTH, 40);
		expectEquals("自动宽度的内边距", ConfigSpec.Bottom.BUTTON_PADDING, 16);
		expectEquals("按钮间距", ConfigSpec.Bottom.GAP, 6);

		bottomPage.bottom.items.get(2).onClick.run();
		expectEquals("链接按钮真的去开网页", smile_libbox.api.ui.Ui.lastLink,
				"https://example.com");

		// ---------- 底部按钮的对齐 ----------
		System.out.println("[底部行] 左右对齐");
		ConfigSpec alignSpec = new ConfigSpec("t", null, null);
		ConfigSpec.Page alignPage = alignSpec.page("p");
		expectEquals("默认左对齐", alignPage.bottom.align, ConfigSpec.Bottom.Align.LEFT);
		alignPage.bottom.button(() -> "左", () -> { });
		expectEquals("默认加进来的项是左对齐", alignPage.bottom.items.get(0).align,
				ConfigSpec.Bottom.Align.LEFT);
		alignPage.bottom.align(ConfigSpec.Bottom.Align.RIGHT);
		alignPage.bottom.button(() -> "右", () -> { });
		expectEquals("改默认后新项靠右", alignPage.bottom.items.get(1).align,
				ConfigSpec.Bottom.Align.RIGHT);
		expectEquals("改默认不影响老项", alignPage.bottom.items.get(0).align,
				ConfigSpec.Bottom.Align.LEFT);
		alignPage.bottom.button(ConfigSpec.Bottom.Align.LEFT, () -> "左二", () -> { });
		expectEquals("单项可指定对齐", alignPage.bottom.items.get(2).align,
				ConfigSpec.Bottom.Align.LEFT);
		alignPage.bottom.text(ConfigSpec.Bottom.Align.RIGHT, () -> "状态", 0);
		expectEquals("文字项也认对齐", alignPage.bottom.items.get(3).align,
				ConfigSpec.Bottom.Align.RIGHT);
		alignPage.bottom.items.get(1).align(ConfigSpec.Bottom.Align.LEFT);
		expectEquals("last().align 能改这一项", alignPage.bottom.items.get(1).align,
				ConfigSpec.Bottom.Align.LEFT);
		alignPage.bottom.align(null);
		expectEquals("align(null) 退回左对齐", alignPage.bottom.align,
				ConfigSpec.Bottom.Align.LEFT);
		alignPage.bottom.link(ConfigSpec.Bottom.Align.RIGHT, () -> "文档", "https://example.com");
		expectEquals("网页按钮也能指定对齐", alignPage.bottom.items.get(4).align,
				ConfigSpec.Bottom.Align.RIGHT);

		// ---------- 完成的开关 / 重置的开关 ----------
		System.out.println("[系统按钮] 完成与重置都可关");
		ConfigSpec systemSpec = new ConfigSpec("t", null, null);
		expectEquals("默认显示完成", systemSpec.showDone, Boolean.TRUE);
		expectEquals("默认显示重置", systemSpec.showReset, Boolean.TRUE);
		expectEquals("默认有系统按钮", systemSpec.hasSystemButtons(), Boolean.TRUE);
		systemSpec.showDone(false).showReset(false);
		expectEquals("可以都关掉", systemSpec.showDone + "," + systemSpec.showReset, "false,false");
		expectEquals("都关掉后没有系统按钮", systemSpec.hasSystemButtons(), Boolean.FALSE);
		expectEquals("默认完成动作是关闭界面", systemSpec.doneAction, null);
		systemSpec.doneAction("保存并退出", () -> { });
		expectEquals("可以换文字", systemSpec.doneLabel, "保存并退出");

		ConfigSpec switchSpec = new ConfigSpec("t", null, null);
		switchSpec.showSystemButtons(false);
		expectEquals("总开关一次关两颗", switchSpec.showDone + "," + switchSpec.showReset,
				"false,false");
		expectEquals("总开关关掉后没有系统按钮", switchSpec.hasSystemButtons(), Boolean.FALSE);
		switchSpec.showDone(true);
		expectEquals("单开回完成也算有系统按钮", switchSpec.hasSystemButtons(), Boolean.TRUE);
		switchSpec.showSystemButtons(true);
		expectEquals("总开关打开两颗都回来", switchSpec.hasSystemButtons(), Boolean.TRUE);

		// JSON 里的系统按钮开关
		String systemJson = "{\"title\":\"t\",\"systemButtons\":false,\"showDone\":true,"
				+ "\"doneLabel\":\"mymod.gui.save_exit\",\"pages\":[{\"title\":\"p\",\"rows\":[]}]}";
		ConfigSpec jsonSystem = ConfigLayout.parse(systemJson, null, null, null);
		expectEquals("JSON：总开关关掉重置", jsonSystem.showReset, Boolean.FALSE);
		expectEquals("JSON：具体的 showDone 覆盖总开关", jsonSystem.showDone, Boolean.TRUE);
		expectEquals("JSON：doneLabel 翻成当前语言", jsonSystem.doneLabel,
				"mymod.gui.save_exit");
		ConfigSpec jsonSystemOff = ConfigLayout.parse(
				"{\"title\":\"t\",\"systemButtons\":false,\"pages\":[{\"title\":\"p\",\"rows\":[]}]}",
				null, null, null);
		expectEquals("JSON：只写总开关就没有系统按钮", jsonSystemOff.hasSystemButtons(),
				Boolean.FALSE);

		// ---------- 字符串行 ----------
		System.out.println("[字符串行] 正则校验 / 默认值 / 复位");
		ConfigSpec strSpec = new ConfigSpec("t", null, null);
		ConfigSpec.Page strPage = strSpec.page("p");
		strPage.string("dir", "L", "tip", "C:\\a", "C:\\default", value -> { });
		ConfigSpec.Row dirRow = strPage.last();
		expectEquals("字符串行类型", dirRow.kind, ConfigSpec.Kind.STRING);
		expectEquals("值记下来了", dirRow.stringValue, "C:\\a");
		expectEquals("没写正则 → 什么都收", dirRow.stringError("随便写 123"), null);
		expectEquals("不是默认值", dirRow.isDefaultValue(), Boolean.FALSE);
		expectEquals("可复位", dirRow.resettable(), Boolean.TRUE);
		dirRow.resetToDefault();
		expectEquals("复位到默认", dirRow.stringValue, "C:\\default");
		expectEquals("复位后算默认值", dirRow.isDefaultValue(), Boolean.TRUE);
		expectEquals("字符串行默认正则名为文本", dirRow.regexName(), "smile_libbox.gui.regex.text");

		dirRow.regex("[A-Za-z]:\\\\.*", "本地文件夹");
		expectEquals("自定义正则：匹配", dirRow.stringError("D:\\mc"), null);
		expectEquals("自定义正则：不匹配", dirRow.stringError("mc"), "pattern");
		expectEquals("自定义正则名", dirRow.regexName(), "本地文件夹");

		ConfigSpec.Row fileRow = strPage.string("file", "F", "tip", "a.txt", "a.txt", null).last();
		fileRow.regexFile("txt");
		expectEquals("文件正则：.txt 通过", fileRow.stringError("b.TXT"), null);
		expectEquals("文件正则：.json 不通过", fileRow.stringError("b.json"), "pattern");

		ConfigSpec.Row brokenRow = strPage.string("broken", "B", "tip", "", "", null).last();
		brokenRow.regex("[");
		expectEquals("非法正则 → 不拦", brokenRow.stringError("任意"), null);

		// JSON 里的字符串行
		String stringJson = "{\"title\":\"t\",\"pages\":[{\"title\":\"p\",\"rows\":["
				+ "{\"type\":\"string\",\"key\":\"dir\",\"label\":\"D\",\"default\":\"C:\\\\x\","
				+ " \"regexFile\":\"json\"},"
				+ "{\"type\":\"string\",\"key\":\"plain\",\"label\":\"P\",\"default\":\"hi\"}"
				+ "]}]}";
		ConfigSpec stringSpec = ConfigLayout.parse(stringJson, null, null, null);
		ConfigSpec.Row jsonDir = stringSpec.pages.get(0).rows.get(0);
		ConfigSpec.Row jsonPlain = stringSpec.pages.get(0).rows.get(1);
		expectEquals("JSON：string 行类型", jsonDir.kind, ConfigSpec.Kind.STRING);
		expectEquals("JSON：默认值", jsonDir.stringValue, "C:\\x");
		expectEquals("JSON：regexFile 生效", jsonDir.stringError("a.json"), null);
		expectEquals("JSON：regexFile 拦住别的后缀", jsonDir.stringError("a.txt"), "pattern");
		expectEquals("JSON：没写正则的字符串行不校验", jsonPlain.stringError("!!!"), null);

		// ---------- 仅输入框的数值行 ----------
		System.out.println("[仅输入框] 代码定义 / JSON 定义");
		ConfigSpec boxSpec = new ConfigSpec("t", null, null);
		ConfigSpec.Page boxPage = boxSpec.page("p");
		boxPage.slider("slider_row", "S", null, 5, 1, 50, 5, v -> { });
		ConfigSpec.Row sliderRow = boxPage.last();
		boxPage.slider("box_row", "B", null, 600, 60, 3600, 600, v -> { });
		ConfigSpec.Row boxRow = boxPage.last().boxOnly();
		boxPage.number("number_row", "N", null, 600, 60, 3600, 600, v -> { });
		ConfigSpec.Row numberRow = boxPage.last();
		boxPage.toggle("flag", "F", null, false, false, v -> { });
		boxPage.last().boxOnly();

		expectEquals("滑条行默认不是仅输入框", sliderRow.onlyBox(), Boolean.FALSE);
		expectEquals("boxOnly() 后成立", boxRow.onlyBox(), Boolean.TRUE);
		expectEquals("boxOnly 行仍是数值行", boxRow.kind, ConfigSpec.Kind.SLIDER);
		expectEquals("boxOnly 行保留 min/max", boxRow.min, 60);
		expectEquals("boxOnly 行保留默认正则", boxRow.regex,
				ConfigSpec.Row.DEFAULT_NUMBER_REGEX);
		expectEquals("page.number() 一步到位", numberRow.onlyBox(), Boolean.TRUE);
		expectEquals("开关行调用 boxOnly() 无效",
				boxPage.last().onlyBox(), Boolean.FALSE);
		boxRow.intValue = 900;
		expectEquals("重置回默认", boxRow.resetToDefault(), Boolean.TRUE);
		expectEquals("boxOnly 行复位后算默认值", boxRow.isDefaultValue(), Boolean.TRUE);

		String boxJson = "{\"title\":\"t\",\"pages\":[{\"title\":\"p\",\"rows\":["
				+ "{\"type\":\"number\",\"key\":\"ttl\",\"label\":\"T\","
				+ " \"min\":60,\"max\":3600,\"default\":600},"
				+ "{\"type\":\"slider\",\"key\":\"boxed\",\"label\":\"B\","
				+ " \"min\":1,\"max\":10,\"default\":5,\"boxOnly\":true},"
				+ "{\"type\":\"slider\",\"key\":\"plain2\",\"label\":\"P\","
				+ " \"min\":1,\"max\":10,\"default\":5}"
				+ "]}]}";
		ConfigSpec boxParsed = ConfigLayout.parse(boxJson, null, null, null);
		expectEquals("JSON：type=number 变成仅输入框",
				boxParsed.pages.get(0).rows.get(0).onlyBox(), Boolean.TRUE);
		expectEquals("JSON：slider + boxOnly 生效",
				boxParsed.pages.get(0).rows.get(1).onlyBox(), Boolean.TRUE);
		expectEquals("JSON：没写的仍是滑条",
				boxParsed.pages.get(0).rows.get(2).onlyBox(), Boolean.FALSE);

		System.out.println("结果：通过 " + passed + " 项，失败 " + failed + " 项");
		if (failed > 0) {
			System.exit(1);
		}
	}
}
