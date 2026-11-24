# Smile's Lib Box

[English](README.md)

一个跨版本前置库，做两件事：把「当前是什么版本、有哪些能力」收敛成一套固定接口；
把版本间变化的 Minecraft 接口（Screen 覆写签名、键位工厂、输入事件、讲述人、
上下文条注入）按版本编译成适配层，交给依赖方调用。

框架由官方 Fabric CLI（`fabric init`）生成。产物按版本出 jar：
`smile_libbox-1.0.4+1.20.1.jar` … `+1.21.11.jar`（8 个），外加一个不绑定
游戏版本的 `+generic.jar`。`smile_libbox.api` 下不引用任何 `net.minecraft.*`
类型（已核对 class 常量池），这部分同一份 class 能装到所有版本；
`smile_libbox.client.compat` / `mixin` 下的适配层按版本编译，refmap 由该版本
的 MC jar 生成。安装时按游戏版本选对应 jar 放进 `mods/`；没有精确匹配的版本用
`+generic.jar`（只提供纯判断 API，不启用任何需要注入 MC 的适配层）。

---

# 给模组作者：怎么判断版本

这一节是给**使用本库的模组作者**看的。你不需要为每个游戏版本出包，也不需要
Stonecutter 之类的条件编译；只需要两步：判断版本 / 能力，然后加载对应实现。

## 1. 先选对判断依据

| 你想判断什么 | 用哪个接口 | 例子 |
| --- | --- | --- |
| 游戏版本区间 | `GameVersion` / 表达式 | `GameVersion.atLeast("1.21.9")`、`Conditional.matches(">=1.21.9")` |
| 数据包格式版本 | `MinecraftVersions` | `dataPack()`、`dataPackMinor()`、`dataPackAtLeast(15, 0)`、`Conditional.matches("datapack:>=15.0")` |
| 资源包格式版本 | `MinecraftVersions` | `resourcePack()`、`resourcePackMinor()`、`resourcePackAtLeast(75, 0)`、`Conditional.matches("resourcepack:>=75.0")` |
| 协议 / 世界数据版本 | `MinecraftVersions` | `protocol()`、`world()`、`protocolAtLeast(763)`、`worldAtLeast(3465)`、`protocol:>=763`、`world:>=3465` |
| 游戏版本 / Java 版本 | `MinecraftVersions` | `game()`、`name()`、`java()`、`summary()` |
| 某个 ABI 能力是否存在 | `Compat` / `cap:` | `Compat.eventBasedInput()`、`Conditional.matches("cap:dataComponents")` |
| 某个模组 / 类是否安装 | `ModPresence` / `mod:` `class:` | `ModPresence.loaded("vanillabackport")`、`class:net.minecraft.client.KeyMapping$Category` |
| 多个条件同时满足 | `Conditional` | `Conditional.matches(">=1.21.9 & mod:vanillabackport")` |

**优先按「能力 / 数据版本」判断，别只按游戏版本。** 整合包里常有把高版本特性搬回低版本的
backport 模组，快照版的版本号也不是数字递增；判断「数据包格式 ≥ 15」或「类 X 是否存在」
通常比判断「游戏版本 ≥ 1.20.5」更贴合你要用的那个 API。

所有数值来自客户端 jar 根目录的 `version.json`（纯 JDK 读取，零 MC 依赖）。
1.21.9 之前 `pack_version` 是 `{"resource":15,"data":15}`，之后改成
`{"resource_major":75,"resource_minor":0,"data_major":94,"data_minor":1}`；
本库已把它们**统一成主版本 + 次版本**，旧结构次版本补 0，不需要你自己分支：

| 环境 | 游戏版本 | 数据包 | 资源包 | 协议 | 世界 | Java |
| --- | --- | --- | --- | --- | --- | --- |
| 1.20.1 | `1.20.1` | `15.0` | `15.0` | 763 | 3465 | 17 |
| 1.21.11 | `1.21.11` | `94.1` | `75.0` | 774 | 4671 | 21 |

条件表达式完整支持：`*` / 空、`>=1.21.9`、`>1.20.1`、`<1.20.2`、`<=1.21.8`、
`==1.21.11`、`!=1.20.1`、`mod:xxx`、`!mod:xxx`、`class:xxx`、`cap:xxx`、
`datapack:>=15.0`、`resourcepack:>=75.0`、`protocol:>=763`、`world:>=3465`
（后四类支持 `>= > <= < == !=`），用 `&` 连接表示全部满足。

## 2. 常见版本分界速查

判断出来之后该改什么？下表是 1.20.1 ~ 1.21.11 之间真正会改变编译签名的分界。
`Compat` 已把它们做成固定布尔，直接用，不必自己记版本号：

| 分界 | 变化 | 判断方式 |
| --- | --- | --- |
| 1.20.2 | `renderBackground` 增加窗口参数；`mouseScrolled` 增加水平分量 | `Compat.fourArgBackground()` / `cap:fourArgBackground` |
| 1.20.5 | 物品栈改用 DataComponent，NBT 读写接口整体替换 | `Compat.dataComponents()` / `cap:dataComponents`；内容相关逻辑建议改用 `datapack:` 判断 |
| 1.21 | `ResourceLocation` 构造改为静态工厂 | `Compat.resourceLocationFactory()` / `cap:resourceLocationFactory` |
| 1.21.5 | `Narrator.say` 增加音量参数 | `Compat.narratorVolume()` / `cap:narratorVolume` |
| 1.21.9 | 输入回调改事件对象、键位分类对象化、资源类名变化、快捷栏上方改为上下文条 | `Compat.eventBasedInput()` / `cap:eventBasedInput`、`Compat.contextualBar()` |
| 1.21.9 | `pack_version` 结构改成 `*_major` / `*_minor` | 无需判断，`MinecraftVersions` 已归一化 |

## 3. 怎么让依赖本库的模组是单 jar

> **前提**：下面的「拆类 + 延迟加载」只在**同一个命名空间代内**成立（1.20.1~1.21.11 的
> intermediary 代）。26.x 起换成 Mojang 官方名，编译期类名不同，靠延迟加载跨不过去。
> 要让**使用方也保持单 jar 并跨代**，用本库现在的做法：把 MC 类型全部关进按代编译的
> 适配层，主 jar 只依赖 `smile_libbox.api.ui` 这套纯 Java 契约
> （`Ui` / `UiScreen` / `UiCanvas` / `UiHost` / `ConfigSpec` / `KeyRef`），
> 常量池零 `net/minecraft` 引用；库按命名空间分别出 jar 提供 `Ui.Backend` 实现。
> 已在 26.2 正式整合包实测通过，见「验证记录」。

**为什么不能直接写两套覆写**：`Screen#keyPressed` 在 1.21.9 之前是
`keyPressed(int, int, int)`，之后是 `keyPressed(KeyEvent)`，而 `KeyEvent` 在 1.20.1 不存在。
同一个 class 里放不下两套签名，构建期条件编译也只会让你出多个 jar。

**可行做法**：jar 只是容器，JVM 类加载是延迟的，字符串类名不产生符号引用。

1. 把只在某个版本区间可用的实现**拆成独立类**，放进各自的包；
2. 主流程只依赖一个**稳定接口**（由你自己定义，方法签名里不含任何 MC 类型）；
3. 运行时用 `Conditional` 判断，命中才 `Class.forName` 并实例化；
4. 条件不成立的类永远不被加载，自然不会因为引用了不存在的类型而抛
   `NoClassDefFoundError`；所有实现一起打进**同一个 jar**。

类布局：

```
mymod/
  MyMod.java                       ← 只引用稳定接口，绝不 import 版本相关类
  api/BarHooks.java                ← 稳定接口，方法签名只含 JDK / 本库类型
  compat/legacy/LegacyHooks.java   ← < 1.21.9 实现，可引用旧版类型
  compat/v1219/ModernHooks.java    ← >= 1.21.9 实现，可引用 KeyEvent 等
```

可复制的范式：

```java
// 稳定接口：签名里没有任何 MC 类型
public interface BarHooks {
    void onTick();
    boolean wantsLocatorBar();
}

// 入口：只写字符串类名
public final class MyMod implements ModInitializer {
    private static BarHooks hooks;

    @Override public void onInitialize() {
        hooks = Conditional.choose(BarHooks.class,
                ">=1.21.9              => mymod.compat.v1219.ModernHooks",
                "mod:vanillabackport   => mymod.compat.backport.BackportHooks",
                "*                     => mymod.compat.legacy.LegacyHooks");
        // 一次性动作也可以直接条件执行
        Conditional.when(">=1.20.5", "mymod.compat.v1205.ComponentSetup");
    }
}

// 功能开关 / 配置界面显隐：纯判断
if (Conditional.matches(">=1.21.9")) {
    // 显示高版本专属选项
}
```

`choose` 从上往下取第一条条件成立的规则，返回稳定接口实例；实现类需要公开无参构造器
（本库会 `setAccessible(true)`）。任何加载 / 实例化失败都只记日志并返回 `null`，
不会让游戏崩。单条动作可以用 `when(expr, 类名)` 或 `run(类名)`，实现类实现
`Conditional.Action` 或 `Runnable`。

**Mixin 也由模组自己门控**：继承 `VersionedMixinPlugin`，在 `accepts()` 里写规则，
再在 mixin 配置的 `"plugin"` 里填你的子类名。返回 false 时该 mixin 类在进入解析阶段前就被跳过。

```java
public final class MyMixinPlugin extends VersionedMixinPlugin {
    @Override protected boolean accepts(String target, String mixin) {
        if (mixin.contains(".compat.v1219.")) return matches(">=1.21.9");
        if (mixin.contains(".compat.legacy.")) return matches("<1.21.9");
        return true;
    }
}
```

库自己的 `LibBoxMixinPlugin` 就是这么做的（按 `.v1201.` / `.v1202.` / `.v1215.` /
`.legacy.` / `.modern.` 包名片段门控 ABI 簇）。

### 验证记录

**已实测的环境**（库启动时会打印两行日志，排查版本判断问题先看它们）：

```
(smile_libbox) 版本环境：mc=1.20.1  protocol=763 world=3465 data=15.0  resource=15.0 java=17
(smile_libbox) 版本环境：mc=1.21.11 protocol=774 world=4671 data=94.1  resource=75.0 java=21
(smile_libbox) 版本环境：mc=26.2    protocol=776 world=4903 data=107.1 resource=88.0 java=25
(smile_libbox) 门控自检：>=1.20=true, datapack:>=15.0=true, resourcepack:>=15.0=true, ...
```

`>=` / `datapack:` / `resourcepack:` / `protocol:` / `world:` / `mod:`
六类表达式在三版均按预期求值，库自身没有引发任何异常。`94.1 / 75.0`、`107.1 / 88.0`
都是 1.21.9 起 `pack_version` 换成 `data_major/data_minor` 后统一出的主.次版本。

| 环境 | 加载方式 | 结果 |
| --- | --- | --- |
| 1.20.1 `runClient` | `+1.20.1.jar` + toolb 单 jar | **通过**：进主界面（`Sound engine started`），三条初始化日志齐全，无 Mixin 失败 |
| 1.21.11 `runClient` | 旧单 jar 库 | 进主界面，自检正确（改造前的记录） |
| 26.2 正式整合包 | `+26.2.jar` + toolb 单 jar，隔离 gameDir + `-Dfabric.addMods` | **通过**：`Loading 148 mods` 接受两个 jar，进主界面，零我方异常 |

**配置项按版本显示 / 隐藏（2026-11-07 实机）**：Smile's toolb 的「玩家追踪条」
两个开关由 `GameVersion.atLeast("1.21.9")` 门控。26.2 的 `atLeast` 为 true
（语义版本比较，major 26 > 1），配置界面显示两个追踪条开关（补齐接管前 12 行 /
4 开关，含一行降级提示；补齐后 11 行 / 4 开关）；
1.20.1 为 false，9 行 / 2 开关、追踪条开关隐藏而其它配置项正常，两边都无崩溃。
这是「行为差异用运行期版本判断，不必为每个版本单独打包」的直接用例：
**判断逻辑写在使用方的单 jar 里，只要不引用 MC 类型，就能跨命名空间代生效**。
注意别在 `ClientModInitializer` 里构造完整界面数据做自检——那会经过
`Minecraft.options`，两代实测都会 NPE；入口阶段只做纯 Java 的版本判断。
详见 `Smile's toolb/VERIFY.md` 第 0 节。

**26.2 是怎么跑通的**：最终架构是「库按命名空间出多 jar、toolb 始终保持单 jar」。
26.2 没有 intermediary、没有 refmap，客户端 jar 里直接是官方名
`net/minecraft/client/Minecraft.class`，启动日志 `Mappings not present!`；
整合包 51 个模组没有一个引用 `net/minecraft/class_*`。因此走不了 Loom 重映射，
改由 `tools/build_named26.py` 用 JDK 26 的 `javac --release 25` **直接对 26.2 客户端
jar 编译** `VersionedScreen` / `UiBackend` / `VersionedNarrator` 以及按官方名书写的 client mixin，
产物 `smile_libbox-1.0.4+26.2.jar`（class 版本 69、官方名描述符、无 refmap）。
mixin 的目标类名与注入描述符同样用官方名书写，所以**不需要 refmap 也能注入**。
toolb 单 jar 全部 class 的常量池扫描结果为**零 `net/minecraft` 引用**，
只通过 `smile_libbox.api.ui.Ui` 门面调用库。

实测日志（148 模组，Java 25 / Loader 0.20.1 / fabric-api 0.161.0+26.2）：

```
Loading 148 mods: - smile_libbox 1.0.0+26.2 / - smile_toolb 1.0.0
版本环境：mc=26.2 protocol=776 world=4903 data=107.1 resource=88.0 java=25
门控自检：>=1.20=true, datapack:>=15.0=true, ... mod:minecraft=true
Smile's toolb 已加载
讲述人已切换到系统语音引擎
Sound engine started
```

无 `Crash Report`、无 `ModResolutionException`、无 `NoClassDefFoundError`、
无 `Mixin apply ... failed`、无我方类名的错误行。

**改造前失败过，原因留作对照**：最早用 `+generic.jar` 时崩溃在
`McCalls.keyMapping` → `NoClassDefFoundError: net/minecraft/class_304`，
ModMenu 集成同时报 `ClassNotFoundException: net.minecraft.class_437`。
当时 toolb 单 jar 仍有 262 处 intermediary 符号引用，`+generic.jar` 的适配层也带着它们
（`McCalls` 12 处、`VersionedScreen` 6 处）——**类名是编译期写进常量池的**，
`Conditional` 延迟加载、`Compat` 反射、库的多 jar 都改不了。解决方案就是上面这套
「toolb 零 MC 引用 + 库按代出 jar」，而不是放宽 `depends.minecraft`。
当时删掉的 Cloth Config / ModMenu 也是同理：它们的 builder 会把 `Component` / `Screen`
写进调用方方法描述符，跨命名空间必断。

**26.2 的功能边界（2026-11-07 晚补齐）**：最初 `+26.2.jar` 只编译了界面适配层，
`InputEvents` / `BarEvents` 不触发，两个状态条开关只是「显示但不生效」。现在这一代
也有自己的 client mixin，`Hud` / `Screen` / `MouseHandler` 三个注入点全部按 26.2
官方名编译，所以上下文条接管、自定义退出按键、全局鼠标退出键在 26.2 同样走业务代码：

| 26.2 的注入点 | 低版本对应 | 作用 |
| --- | --- | --- |
| `Hud#nextContextualInfoState` | `Gui#nextContextualInfoState`（1.21.9+） | 把四状态枚举交给 `BarEvents`，两个追踪条开关真正生效 |
| `Screen#keyPressed(KeyEvent)` | `Screen#keyPressed(int,int,int)` | 自定义退出键 |
| `MouseHandler#onButton(long,MouseButtonInfo,int)` | `MouseHandler#onPress(long,int,int,int)` | 任意界面里的鼠标退出键 |

26.2 把四状态枚举从 `Gui$ContextualInfo` 挪到了 `Hud$ContextualInfo`（常量与顺序不变），
`UiBackend.contextualStates()` 现在两个类都找；`hasWaypoints()` 改走
`ClientPacketListener#getWaypointManager()#hasWaypoints()`；`currentScreen()` 改读
`Minecraft.gui.screen()`，原版自己打开的界面也能被退出键识别。讲述人替换用的是
`com.mojang.text2speech.Narrator` 接口（非 MC 类），本来就在 26.2 生效。

实机证据（隔离实例 `-Dmixin.debug.export=true`）：启动零 `Mixin apply ... failed`，
`状态条特性检测 ... 上下文枚举可用=true`、`配置界面自检 ... 行数=11（开关 4）
玩家追踪条开关=显示 上下文条接管=可用`（降级提示行自动消失，比之前少 1 行），
`Sound engine started`；导出的 `Hud.class` / `Screen.class` / `MouseHandler.class`
反编译后分别含 `BarEvents.dispatch` / `InputEvents.dispatchScreenKey` /
`InputEvents.dispatchMouseButton` 调用。**仍需人工进世界走查**的是游戏内效果
（按 Tab 切定位条、骑乘蓄力条、录制退出键），无人值守的启动验证覆盖不到这些交互。

有个真实环境才看得见的坑：开发 classpath 上**同时存在 clientOnly 与 common 两份
`version.json`**，别的 jar 也可能带同名资源。`MinecraftVersions` 会取 id 与 Loader
报告一致的那份；你自己读资源时也要做同样的筛选。

**离线探针**：用一份 `version.json` 切换运行环境、同一个探针（含一个「只在 1.21.9 存在的类型」）实测：

- **1.20.1**：`matches(">=1.21.9") = false` → `choose` 返回兜底实现；高版本实现类的
  静态初始化块**没有执行**，即使模拟的新类型不在 classpath 也不报 `NoClassDefFoundError`；
- **1.21.11**：`matches(">=1.21.9") = true` → 加载高版本实现并成功解析那个新类型。

另用两个版本的真实 `version.json` 验证了 `dataPackAtLeast` / `resourcePackAtLeast` /
`protocolAtLeast` / `worldAtLeast` 以及 `datapack:` / `resourcepack:` / `protocol:` /
`world:` 表达式：例如 `datapack:>=94.1` 在 1.20.1 为 false、在 1.21.11 为 true，
`resourcepack:>=75.0`、`protocol:>=774`、`world:>=4671` 同理。

即「拆类 + 延迟加载」这条路径**在同一命名空间代内**对普通类成立。
Mixin 的 refmap 与跨代际的编译期类名问题都不是运行时判断能解决的；
跨代请改用 `smile_libbox.api.ui` 纯 Java 门面 + 库按代出 jar，
见「给作者的注意事项」与「构建与安装」。

---

# 让模组自己决定要不要注入（含联动其它 mod）

**结论先给**：判断权 100% 在**使用方模组**手里。库只提供判断依据与门控基类，从不替使用方
决定某个 mixin 该不该生效；使用方在自己的单 jar 里按当前游戏版本、能力、装了哪些模组，
决定「这个版本要不要注入」以及「用哪一份注入类」。唯一例外是**注入方法描述符含 MC 类型**
时，注入类必须由库按版本编译 —— 但「要不要用」仍由使用方的 plugin 决定。

## 通道 0：按版本门控自己的 mixin（最常用）

判断发生在 **mixin 配置解析阶段**，不是游戏运行时：继承 `VersionedMixinPlugin`，
`accepts()` 返回 false 的 mixin 类**连解析都不会发生**，因此它引用了当前版本不存在的
类型也不会炸。判断依据只能是纯 Java 数据（`version.json`、classpath 资源、Loader 模组列表），
因为这个阶段 MC 类还没加载。

```java
public final class MyMixinPlugin extends VersionedMixinPlugin {
    @Override protected boolean accepts(String target, String mixin) {
        if (mixin.endsWith("LegacyBarMixin")) return matches("<1.21.9");
        if (mixin.endsWith("ModernBarMixin")) return matches(">=1.21.9");
        if (mixin.endsWith("BackportMixin"))  return matches("mod:vanillabackport");
        return true;
    }

    // 也可以按版本动态追加要加载的 mixin 类，配置里的 client 数组留空
    @Override public List<String> getMixins() {
        return matches(">=1.21.9")
                ? List.of("mymod.client.mixin.ModernBarMixin")
                : List.of("mymod.client.mixin.LegacyBarMixin");
    }
}
```

`getMixins()` 返回的是**追加**到配置里的类名（相对 `package`）；配合
`required: false` + `defaultRequire: 0`，版本不匹配的类名根本不会进解析队列。
`VersionedMixinPlugin` 把 `getRefMapperConfig()` 固定为 `null`，异常全部吞掉并记日志。

```json
{
  "required": false,
  "package": "mymod.client.mixin",
  "compatibilityLevel": "JAVA_17",
  "plugin": "mymod.client.mixin.MyMixinPlugin",
  "client": [],
  "injectors": { "defaultRequire": 0 }
}
```

**两条硬边界**：

- 别把判断写进 mixin 类的方法体里（`if (GameVersion.atLeast("1.21.9")) ...`）。
  那时类已经被解析，对不上的注入描述符会直接让注入失败；`require = 0` 只能让它静默失效，
  做不到「按版本生效」。判断必须在 `shouldApplyMixin()`（解析前）完成。
- 跨 1.20.1（intermediary）/ 26.2（named）两代时，含 MC 类型的 mixin class
  **无法在单 jar 里同时带两套描述符**。这种情况走通道 B；只有描述符不含 MC 类型的
  软目标 mixin 才能留在单 jar 里由使用方自己门控。

下面只讨论**目标不是 MC 类**的联动，门控方式沿用通道 0；业务判断一律交给
`smile_libbox.api.link.ModLinks` 的回调。库不会因为「要联动」就多出一个 jar ——
联动按「MC 命名空间代际」切分，不按「第三方模组」切分。

| 先问自己 | 结论 |
| --- | --- |
| 第三方 mod 有公开 API / 事件？ | 直接反射调用，零 mixin（`BackportBridge` 是只读例子） |
| 要注入，且注入方法描述符里**不含任何 MC 类型**？ | **通道 A**：使用方单 jar 自持薄注入 + 注册回调 |
| 描述符含 MC 类型（`GuiGraphics` / `PoseStack` / `Player` …）？ | **通道 B**：库按版本出联动 mixin，使用方只注册回调 |
| 多个使用方要共用同一切入点？ | 一律通道 B，避免重复注入互相覆盖 |

关键点：第三方模组的**类名不是** `net/minecraft/*`。字符串 `@Mixin(targets = "...")` +
`remap = false` 不参与 refmap，所以目标类名跨 intermediary / named 两代都稳定；真正会断的是
`method = "..."` 里的**参数/返回值描述符**。`shouldSkipRendering()Z` 这种无参、返回基本类型的
切入点是安全的；`render(GuiGraphics, float, ...)` 在 1.20.1、1.21.9、26.2 三套命名空间里
写法全不一样，只能交给库按版本编译。

## 通道 A：使用方单 jar 自持注入点（推荐）

toolb 的 VanillaBackport 联动就是这条路径，四个文件：

**1. 薄注入类** —— 只做一次派发，不 import 配置类、不 import 目标 mod 的业务类：

```java
@Mixin(targets = "com.example.backport.client.LocatorBarRenderer", remap = false)
public abstract class MyBackportMixin {
    private static final String HOOK = "example.locator_bar.skip";

    @Inject(method = "shouldSkipRendering()Z", at = @At("RETURN"),
            cancellable = true, remap = false, require = 0)
    private void mymod$choose(CallbackInfoReturnable<Boolean> cir) {
        Boolean value = ModLinks.dispatchBoolean(HOOK, cir.getReturnValue());
        if (value != null) cir.setReturnValue(value);
    }
}
```

**2. 门控 plugin** —— 继承库的 `SoftTargetMixinPlugin`，只声明「联动谁」：

```java
public final class MyBackportPlugin extends SoftTargetMixinPlugin {
    protected String modId() { return "examplebackport"; }
    protected String[] markerClasses() {
        return new String[]{"com.example.backport.client.LocatorBarRenderer"};
    }
    // 可选：protected String when() { return ">=1.20.1"; }
}
```

基类按顺序检查 `Conditional.matches(when())` → mod id → 所有标记类；mixin 极早期
Loader 未就绪时，标记类还会走 classpath 资源探测兜底。它把 `getRefMapperConfig()`
固定返回 `null`（软目标不 remap）。

**3. mixin 配置** —— 独立文件、`required: false`、`defaultRequire: 0`：

```json
{
  "required": false,
  "package": "mymod.client.mixin",
  "compatibilityLevel": "JAVA_17",
  "plugin": "mymod.client.mixin.MyBackportPlugin",
  "client": ["MyBackportMixin"],
  "injectors": { "defaultRequire": 0 }
}
```

在 `fabric.mod.json` 里单独注册一项，`"environment": "client"`，**不要**塞进主配置。

**4. 业务回调** —— 普通 Java 类，在 client 入口注册：

```java
public final class MyBackportLink {
    public static void register() {
        ModLinks.onOverride("example.locator_bar.skip", MyBackportLink::choose);
    }
    private static Object choose(String hook, Object original) {
        // original 是第三方 mod 原本的返回值；返回 null = 不干预
        return MyConfig.get().forceBar ? Boolean.TRUE : null;
    }
}
```

`ModLinks` 支持同一 hook 多个订阅者（按注册顺序取第一个非 `null` 的覆盖值）、
`emit(hook, payload)` 通知型钩子、`active(hook)` / `subscribers(hook)` 供配置界面显示
「联动是否已接管」，以及 `describe()` 一行启动自检。回调异常逐个隔离，不会拖垮
第三方模组的方法。注入类里的 `HOOK` 字面量必须与回调注册的字符串一致 —— 这是跨 jar
的唯一契约，编译期不产生任何符号引用。

## 通道 B：描述符涉 MC 类型时交给库

放在库侧的原因只有一条：mixin 的 `method` 描述符由**编译期所用的 MC jar** 决定，
`remap = false` 也救不了。库已经按版本出 jar，正好让每个 jar 带一份对得上描述符的
联动注入类，使用方代码一行都不用改（hook id 不变）。

落点：

1. `src/client/java/smile_libbox/client/mixin/LibExampleMixin.java`，按该 Stonecutter
   节点的 MC 编译，refmap 由 Loom 自动生成；里面同样只写 `ModLinks.dispatch(...)`。
2. 新增 `src/client/resources/smile_libbox.example.mixins.json`（`required: false`、
   `defaultRequire: 0`、plugin 用 `LibBoxMixinPlugin` 或 `SoftTargetMixinPlugin` 的子类），
   并在 `src/main/resources/fabric.mod.json` 的 `mixins` 数组追加一项。
3. 26.2 官方名代：在 `named26/src/smile_libbox/client/mixin/` 另写同名类（用官方名描述符），
   把类名加进 `named26/resources/smile_libbox.named26.mixins.json`；
   `tools/build_named26.py` 的 `DROP_EXACT` 追加低版本那份配置名、`metadata()` 的
   `mixins` 列表追加 26.2 那份配置，`verify()` 的 `required` 集合加进新的 class。
4. plugin 除了目标 mod 是否加载，还要用 `libbox-target.txt` 校验装的是本 jar 对应的版本，
   避免装错版本时把对不上的描述符织进游戏。

**库不该预知任意第三方 mod**：只有「多个使用方共用」或「描述符含 MC 类型」的联动才进库，
其余一律通道 A。库也不能反向依赖使用方的配置类，所以联动注入类里只能出现
`ModLinks` 与 mixin 注解。

## 通道 C：玩家 / 世界 / 出站包（World & Packets）

`smile_libbox.api.world` 是给「需要在客户端动手」的模组准备的第二层门面。和 `api.ui`
一样是纯 Java：物品用注册名字符串（`"minecraft:elytra"`、`"minecraft:mace"`），
坐标用 `double[]` / `int`，MC 实体从头到尾不出现在签名里。没装实现（`ready()==false`）
或没有玩家时全部返回安全默认值，调用方不用判空。

### World：状态 + 三个动作

| 用途 | 方法 |
| --- | --- |
| 位置 / 速度 | `pos()` / `motion()`，返回 `double[]{x,y,z}` |
| 落地 / 下落 / 潜行 / 滑翔 | `onGround()` / `fallDistance()` / `sneaking()` / `gliding()` |
| 视线俯仰 | `pitch()`：度，`-90` 向上、`+90` 向下。滑翔时潜行键是原版的「低头加速下降」，要区分「想减速」和「想砸下去」只能看视角 |
| 到地面距离 | `groundDistance()`：脚下第一个固体顶面，默认往下找 48 格，落不到返回 `Double.MAX_VALUE`；`groundDistance(n)` 可指定扫描深度（滑翔砸人要从几十格高空量） |
| 胸甲槽 | `chestItem()` / `chestIsElytra()` |
| 热键栏 | `hotbarSlot(id)` / `selectedSlot()` / `selectSlot(i)` |
| 换护甲 | `equipChestArmor()`：鞘翅退回背包，换上背包里最强的那件（附魔条数优先，其次耐久上限） |
| 重锤 | `maceInInventory()` / `maceToHotbar()`（挑**致密等级最高**的那把；在热键栏就切槽，只在背包就发容器点击包换过来） |
| 世界 | `isSolid(x,y,z)` / `nearbyLiving(r)` / `attackNearest(r)` |
| 实体快照 | `entities(r)`：范围内所有 `isPickable()` 实体的 `EntityView[]`（注册名、脚部坐标、速度、碰撞箱、是否活体），船与盔甲架都在内，掉落物 / 箭 / 经验球不在内；`attackNearest(r)` 优先打活体，没有活体才打船与盔甲架 |
| 界面 | `containerOpen()`：区分「玩家自己在背包里点的」与「模组自动换的」 |

`EntityView` 是纯 Java 值对象（见 `smile_libbox/api/world/EntityView.java`），把实体摊平成
数字，所以依赖方可以做落点预判之类的几何运算，也能脱离游戏跑自检。`World.Backend#entities`
带默认实现（返回空数组），第三方自己实现 `Backend` 时不会被新方法卡住。

版本差异都收在适配层里：`Inventory.selected` 在 1.21.5 变成私有 → 走 `getSelectedSlot()`；
`Entity.fallDistance` 在 1.21.11 变成 `double` → 统一收窄；`getEquipmentSlotForItem`
在 1.21 从静态搬到实例；`Level#getEntitiesOfClass` 在 26.2 移除 → 改 `getEntities(except, aabb, predicate)`。

### Packets：出站容器操作的黑箱拦截

使用方不碰 `ServerboundContainerClickPacket` / `ClickType` / `ContainerInput`，只注册一条规则：

```java
Packets.addRule(action ->
        armorLocked && action.chestSlot && "minecraft:elytra".equals(action.itemId));
```

库在客户端发包的集中入口（intermediary 的 `MultiPlayerGameMode#handleInventoryMouseClick`、
26.2 的 `handleContainerInput`）的 `HEAD` 处判定，命中即取消——**包不会发出去，
本地预测也不会发生**，所以界面不抖动。

`Action` 提供 `kind` / `containerId` / `slot` / `button` / `clickType` / `itemId` /
`chestSlot` / `armorSlot`。规则抛异常按「放行」处理，一条坏规则不会卡死所有背包操作。

## 硬性约束清单

- **`remap = false` + 无 refmap**：目标类是第三方 mod 的类，走 Loom 的 refmap 反而会把它
  按 MC 名重映射错。`SoftTargetMixinPlugin` 已把 `getRefMapperConfig()` 固定为 `null`。
- **`required: false` + `require = 0` + 独立配置**：三者缺一，没装目标 mod 时就会崩。
  门控必须在 `shouldApplyMixin()`（解析前）完成，`preApply` 已经太晚。
- **`compatibilityLevel` 要覆盖目标类的 class 版本**：26.2 的游戏类是 class 69，
  库侧 named 配置用 `JAVA_25`；使用方单 jar 若写死 `JAVA_17`，只适用于目标类本身是
  Java 17 及以下的第三方 mod。mixin 0.8.7 支持到 `JAVA_25`。
- **注入类常量池零 `net/minecraft` 引用**：否则「单 jar 跨代」立刻失效。用
  `javap -v` 或常量池扫描核对；`CallbackInfoReturnable<Boolean>` 这类只含 JDK 类型的
  参数是安全的。
- **不要在注入类里 import 目标 mod 的类**：`@Mixin(targets = "字符串")` 足够；一旦出现
  编译期符号引用，目标 mod 不存在时该类连解析都过不去。
- **探测结果缓存**：`ModPresence` / `ModLinks` 的查询很轻，但第三方 mod 的反射桥
  （`BackportBridge`）应只解析一次，失败一律退回「不干预」的安全值。
- **hook id 用小写点分命名**（如 `vanillabackport.locator_bar.skip`），总线内部统一
  `toLowerCase(Locale.ROOT)` 后匹配，避免大小写不一致导致回调永远收不到。

---

# 实例：聊天框草稿（库侧注入 + 使用方持久化）

「关闭聊天框后再打开保留已输入内容、退出服务器后短时间重进保留、换客户端顶号重启保留，
判定以连接地址为准」这类需求，正好演示**描述符涉 MC 类型时怎么分层**：

| 层 | 职责 | 位置 |
| --- | --- | --- |
| 注入点 | 读 / 写 `EditBox`，把 `init()` / `removed()` / `handleChatInput` 转成固定回调 | 库侧 `LibChatDraftMixin`，每个版本 jar 各编一份 |
| 事件总线 | 纯 Java 的 `ChatDraftEvents.onCapture` / `onRestore` | `smile_libbox.api.event`（底包，跨代共用） |
| 业务规则 | 按连接地址分 key、TTL、写盘、清空策略 | 使用方单 jar（toolb 的 `ChatDraftStore`） |

**版本差异**（已逐版本用 mappings / `javap` 核实）：

| 代际 | `ChatScreen#handleChatInput` | 其它注入点 |
| --- | --- | --- |
| 1.20.1 / 1.20.4 | `(String,boolean) boolean` → `CallbackInfoReturnable<Boolean>` | `init()V` / `removed()V` / `@Shadow EditBox input` |
| 1.20.6 ~ 1.21.11 | `(String,boolean) void` → `CallbackInfo` | 同上 |
| 26.2 named | `(String,boolean) void`，描述符直接写官方名、无 refmap | 同上；原版另有会话内草稿 `ChatComponent#saveAsDraft` |

Stonecutter 用 `//? if >=1.20.6` 切换返回类型不同的那一个注入方法；Loom 把另外两个
注入点改写成 `method_25426()V`（init）、`method_25432()V`（removed），
`@Shadow input` 改成 `field_2382:Lnet/minecraft/class_342`。26.2 那份没有 refmap，
注解里保持 `init()V` / `removed()V`。

**三个场景分别怎么命中**：

1. 关闭再打开 → `removed()` 把文本交给 `dispatchCapture`，下次 `init()` 用
   `dispatchRestore` 回填。
2. 退服短时重进 → 使用方按 TTL 判断草稿是否过期；TTL 从最后一次 `removed()` 算起。
3. 换客户端顶号 → 使用方把草稿写到**用户主目录**而不是 `gameDir/config`，
   另一个客户端实例读同一份文件；key 用 `Ui.serverAddress()`（玩家填写的
   `ServerData.ip`），不是玩家名 / 世界名 / 客户端实例。

消息提交时 `handleChatInput` 会 `dispatchCapture(address, "")`，使用方据此删除该地址的
草稿，避免下次打开重复出现。

**但只清空还不够**：原版 `handleChatInput` 只发包、**不清空** `EditBox`（1.20.1 字节码
是 `iconst_1 / ireturn`，1.21.11 是 `return`），随后 `keyPressed` 才 `setScreen(null)`，
于是 `removed()` 里 `input.getValue()` 仍是刚发出去的那条，草稿又被原样存回去 —— 表现
就是「回车发送后重新打开，输入框里还是上次发的内容」。1.21.x 原版用
`exitReason == DONE`（`shouldDiscardDraft()`）规避，1.20.x 根本没有这个字段，
所以库里统一用 `@Unique` 字段记住提交原文，`removed()` 时内容没变就按「发送后关闭」
处理、不落盘；其它 mod 代发命令（参数与输入框内容不同）则不记，避免误删玩家没写完的草稿。

26.2 原版自己也有会话内草稿，所以 `init()` 只在输入框**为空**时回填，不覆盖原版刚保存的内容。

---

# 调试动作（`smile_libbox.api.debug.Debug`）

给外部 AI / 测试工具用的通用门面：`Debug.call(action, args)`，args 与返回值只用 JDK 类型。
支持 `describe` / `screen` / `click` / `key` / `type` / `scroll` / `player` / `entities` /
`world` / `block` / `inventory` / `target` / `command` / `close_screen` / `place_block` /
`use_block` / `use_item` / `container` / `click_slot` / `keybind` / `keybinds`，全部是
**被显式调用才执行**的一次性动作。

**已移除：`control` / `move` / `mine` / `job`（玩家操作类）**。旧实现为了让 AI「按住方向键走路」，
在构造函数里注册了每帧回调，把 `options.keyUp` / `keyJump` / `keySprint` 等 8 个 `KeyMapping`
的 `isDown` 直接写成虚拟状态 —— 虚拟表为空时等于**每 tick 把真实按键全部抹成松开**。
26.x 的输入是按帧从 `KeyMapping` 采样、`KeyMapping.setDown` 只写 `isDown` 不动 `clickCount`，
于是按住前进 / 疾跑看着正常（物理状态被重放），而跳跃这种边沿触发一旦落在这个空窗里就把
按住状态判成松开，表现为「按一下跳跃瞬间刹车」。现在这 4 个动作直接返回明确错误，库里不再
有任何每帧改写输入状态的代码；需要驱动角色请用原版命令或外部按键注入。

---

# 固定接口速查

| 类 | 作用 |
| --- | --- |
| `smile_libbox.api.GameVersion` | `atLeast` / `isOlderThan` / `display` / `current` |
| `smile_libbox.api.MinecraftVersions` | 游戏 / 协议 / 世界 / 数据包 / 资源包 / Java 版本，含 `xxxAtLeast` 比较方法 |
| `smile_libbox.api.Compat` | ABI 分界固定布尔查询；按描述符形状定位 / 调用方法的反射工具 |
| `smile_libbox.api.Conditional` | 条件表达式、延迟加载、`when` / `run` / `choose` / `instantiate` / `describe` |
| `smile_libbox.api.ModPresence` | 模组是否加载、类是否存在（不初始化）、按关键词列举第三方模组 |
| `smile_libbox.api.VersionedMixinPlugin` | 使用方自己门控 mixin 的抽象基类 |
| `smile_libbox.api.SoftTargetMixinPlugin` | 软目标联动门控基类：mod id + 标记类 + 版本表达式 + 资源探测兜底，无 refmap |
| `smile_libbox.api.link.ModLinks` | 第三方联动总线：`onOverride` / `onSignal` / `dispatch` / `dispatchBoolean` / `emit` / `active` / `describe` |
| `smile_libbox.api.event.ChatDraftEvents` | 聊天框草稿总线：`onCapture` / `onRestore` / `dispatchCapture` / `dispatchRestore` / `ready` / `describe` |
| `smile_libbox.api.ui.Ui` | 纯 Java 门面 + `Ui.Backend` 契约；库按命名空间实现，安装前全部返回安全空值 |
| `smile_libbox.api.ui.Ui#whenReady` | 后端安装后执行一次；使用方入口不保证排在库入口之后，凡是要用键位 / 界面 / per-tick 的初始化都包进它 |
| `smile_libbox.api.ui.Ui#onTick` / `#onStarted` | 每帧 / 客户端启动回调；安装之前注册会先入队，`install` 时按注册顺序重放 |
| `smile_libbox.api.ui.Ui#serverAddress` | 当前连接地址（`String`，取 `ServerData.ip`）；草稿与跨客户端功能按它做判定 key |
| `smile_libbox.api.ui.Ui#sendCommand` | 直接向服务端发命令（不开聊天框、不进聊天记录）；返回空串=已发出，否则是 `offline` / `empty` / 异常类名 |
| `smile_libbox.api.ui.Ui#hasCommand` | 服务端是否下发了某个命令根节点（如 Carpet 的 `player`）；主菜单或未连接返回 `false` |
| `smile_libbox.api.ui.UiScreen#drag` / `#release` | 鼠标拖动与松开的纯 Java 回调（默认不处理），滑块类控件用 |
| `smile_libbox.api.ui.UiScreen` / `UiHost` / `UiCanvas` | 使用方界面 / 宿主 / 绘图的纯 Java 契约，签名里无 MC 类型 |
| `smile_libbox.api.ui.ConfigSpec` / `ConfigScreen` | 纯数据描述的配置页 + 库内唯一渲染实现：页签、原版按钮/滑条/输入框、双层滚动条、整行吸附滚动、数字输入实时校验（替代 Cloth Config / MaLiLib 界面） |
| `smile_libbox.api.ui.ConfigLayout` / `ConfigValues` | 第二条定义路线：JSON → 同一个 `ConfigSpec`，值映射到使用方自己的配置文件；两种定义画出来完全一致 |
| `smile_libbox.api.ui.UiHost#numberBox` | 整数输入框：每次按键回调做实时校验，非法内容标红；鼠标悬停在输入框上时才弹**红字** tooltip（合法时不显示，否则会盖住正在键入的文字）；回车提交、`Esc` 由 `UiScreen#beforeKey` 拦下取消；点别处只失焦，不结束输入模式 |
| `smile_libbox.api.ui.ConfigSpec.Page#number` / `Row#boxOnly` | **只有输入框**的数值行：`page.number(key, 标签, tooltip, 当前值, min, max, 默认值, 写回)`，或对已有滑条行写 `page.slider(...).last().boxOnly()`。不创建滑条、也不创建右侧 T/≡ 切换键，控件列整条交给数字框（宽度从 186 变 206）；键入时实时校验、非法标红，但**不写配置** —— 回车 / 关界面 / 点「完成」时才把合法值写回，非法内容整条丢弃并恢复显示原值。适合范围大、滑条拖不准的项（秒数、字数）。JSON 对应 `"type": "number"` 或滑条行加 `"boxOnly": true` |
| `smile_libbox.api.ui.ConfigSpec.Row#regex` / `#regexName` / `#regexFile` / `#numberError` | 数字输入的合法性判定与**两条固定的错误模板**：内容不是正则能接受的 → 「不合法的字符串！仅支持{正则名}」，是数字但越界（含溢出 int）→ 「不合法的数字！仅支持{min}~{max}之间的数据」。正则名默认是内置的「整数」，可用 `regex(pattern, "本地文件夹")` 自定义，或 `regexFile("txt")` 一步得到「.txt格式文件」；JSON 对应 `"regex"` / `"regexName"` / `"regexFile"`。写错正则自动退回只校验整数 |
| `smile_libbox.api.ui.ConfigSpec.Row#requires` / `#available` | 选项联动：一个布尔行可以当任意多个从属行的前置（每个从属行各自 `requires(前置行)`，JSON 写 `"requires": "<前置项的 key>"`）。前置为关时从属行整体禁用 —— 控件走原版灰色禁用态、标签变暗、重置键失效，悬停时 tooltip 先给本行说明，**最后一行红字**「要启用此项，你需要开启『xxx』」 |
| `smile_libbox.api.ui.ConfigSpec.Page#bottom` | **最下面那一行**（就是「全部重置 / 完成」所在的行，不额外占高度）：`bottom.button(文字, 可用性, 点击)` / `bottom.text(文字, 颜色)` / `bottom.link(文字, url)`，与系统按钮共用一行，系统按钮仍钉在右端（一颗都没开时自建项可一直排到右端）。宽度按文字统一计算（不接受自定义宽度）；**对齐可选** —— 默认 `Align.LEFT` 从左往右排，`bottom.align(Align.RIGHT)` 或单项 `Align.RIGHT` 从右往左排（先加的在最右），两边撞上时收窄；文字项排在按钮之后，超宽自动截断。按钮文字与禁用态每帧从 `Supplier` 重取，所以「执行中置灰」直接写在数据里；灰着的按钮悬停显示 `last().disabledTip("…")`，没配就是「该选项暂不可用」 |
| `smile_libbox.api.ui.ConfigSpec#showDone` / `#showSystemButtons` / `#doneAction` / `#showReset` | 右下角的**系统按钮**由模组决定显不显示：`showDone(false)` 只关「完成」（之后只剩 Esc 能退出，适合在非常规位置调用这套页面、或由外层接管关闭），`showReset(false)` 隐藏每行的重置键与「全部重置」并让**控件列向右延长**（滑条更长、切换键右移），`showSystemButtons(false)` 一次关掉两颗 —— 底栏从此整条交给 `page.bottom`，如果模组也没往那里放东西，这一行连高度一起收掉、列表多显示一行。`doneAction("保存并退出", 回调)` 可换掉「完成」的文字与动作。JSON 对应根上的 `"systemButtons"` / `"showDone"` / `"showReset"` / `"doneLabel"`（更具体的单颗开关覆盖总开关） |
| `smile_libbox.api.ui.ConfigSpec.Page#string` | 字符串行：`page.string(key, 标签, tooltip, 当前值, 默认值, 写回)`，用原版 `EditBox` 直接键入。写正则（`regex(...)` / `regexFile(...)`）就实时校验，不合法标红、悬停红字提示「不合法的字符串！仅支持{正则名}」；不写正则什么都收。合法内容每敲一个字写回，非法内容不写回（关界面即丢弃），支持重置。JSON 对应 `"type": "string"` |
| `smile_libbox.api.ui.Ui#openLink` | 用系统浏览器打开链接（`java.awt.Desktop`，纯 Java、不经 MC），没浏览器时静默失败；`bottom.link(...)` 就是它的封装 |
| `smile_libbox.api.ui.UiHost#tooltip` | 用**原版 tooltip** 显示几行文本：宿主按版本调 `renderComponentTooltip`（1.20.1~1.21.5）或 `setComponentTooltipForNextFrame`（1.21.8+ / 26.2）。跟随鼠标、自动换行、自动翻面、原版黑框，使用方不自己画 |
| `smile_libbox.api.ui.UiHost#tooltipLines` / `UiTooltip` | 同一个 tooltip 里逐行上色（`UiTooltip.of` 默认色、`UiTooltip.colored(text, argb)` 指定颜色）；禁用行的「说明 + 红字提示」就是两行两种颜色 |
| `smile_libbox.api.ui.Ui#hasJumpableVehicle` | 只判「可跳跃坐骑」（马/骆驼等）；椅子、船、矿车只是 `hasVehicle()`，拿它去驱动跳跃条会让 26.2 的 `JumpableVehicleBar` 对 `null` 做 `requireNonNull` 崩在 Render Frame |
| `smile_libbox.api.ui.Ui#onlinePlayers` | 当前服务器在线玩家名（含 Carpet 假人），取自 Tab 列表快照 `ClientPacketListener#getOnlinePlayers`；未连接或信息未同步完时返回空列表。1.21.11 起 authlib 7 把 `GameProfile#getName` 改成 record 的 `name()`，库按版本分支 |
| `smile_libbox.api.ui.Ui#onJoin` | 进入世界 / 连上服务器时回调一次（`ClientPlayConnectionEvents.JOIN`），与 `onTick`/`onStarted` 一样支持在库安装前注册并排队重放 |
| `smile_libbox.api.event.BarEvents#jumpStateAllowed` | 上下文条安全闸门：只有原版自己也算出跳跃条、且真有可跳跃坐骑时才允许写回 JUMPABLE_VEHICLE |
| `smile_libbox.api.ui.KeyRef` | 纯 Java 键描述，绕开会被重映射的 `InputConstants.Key` |
| `smile_libbox.client.LibBoxMixinPlugin` | 库内 ABI 簇门控（示例实现，26.2 jar 不使用） |

---

# 给作者的注意事项

- **本库不替你适配任意 MC 接口。** 遇到签名变化，按第 3 节把不同版本的实现拆成独立类、
  定义自己的稳定接口；库不可能替所有模组预置接口。库只预置了一套**通用 GUI / 输入 /
  讲述人门面** `smile_libbox.api.ui`，供「单 jar 使用方」跨命名空间使用（toolb 即用它）。
- **先判断命名空间代际，再判断版本**：这是「依赖本库的模组能否是单 jar」的
  **第一道分界**，比任何版本判断都优先。1.20.1~1.21.11 的正式环境是
  intermediary（`net/minecraft/class_304`），由 Loader 用 mappings 重映射；
  26.x 起 Mojang 直接发布官方名（`net/minecraft/client/KeyMapping`），
  启动日志会出现 `Mappings not present!`，且整合包里其它模组都不再带
  `class_*` 引用。**同一个 class 文件无法跨这道边界**：类名是编译期写进常量池的，
  `Conditional` 的延迟加载、`Compat` 的反射都改变不了它。
  判断方法：看客户端 jar 里是 `net/minecraft/class_310.class` 还是
  `net/minecraft/client/Minecraft.class`，或看启动日志有无 `Mappings not present!`。
  要同时覆盖两代，**库为两代各编译一份 jar**（`+1.20.1`~`+1.21.11` 走 Stonecutter/Loom，
  `+26.2` 走 `tools/build_named26.py` 直接 javac 官方名），
  **使用方则把 MC 类型全部隔离进按代编译的适配层、主 jar 常量池零 `net/minecraft` 引用**，
  只依赖 `smile_libbox.api.ui`。这条路径已在 26.2 实机验证通过。
  注意 `com.mojang.blaze3d.platform.InputConstants` 在正式环境也会被重映射成
  `net/minecraft/class_3675`，用 `KeyRef` 而不是 `InputConstants.Key`。
- **Mixin 的注入目标由编译期 refmap 决定**：intermediary 代里正式环境方法名是
  （`method_xxxxx`），refmap 由你编译时用的那一个 MC 版本生成。`VersionedMixinPlugin`
  只能决定「这个 mixin 用不用」，**不能改变注入描述符**。若你的 mixin 要跨 ABI 簇生效，
  需要给每个簇用对应版本的 MC jar 编译该簇的 mixin class，再把 class 合进同一个 jar
  （合的是 class，产物仍是一个 jar），然后由 plugin 门控。跨命名空间代际时同样无效。
- **按形状找方法，别按名字写死**：正式环境是 intermediary 命名空间，
  `Compat.findMethod(owner, 参数个数, 返回类型, 名字候选...)` 支持只按参数个数 / 返回类型定位；
  `findByShape` 按参数类型形状定位；`call` 失败返回 `null`；`intOf` / `doubleOf`
  可从事件对象里反射读字段式访问器。
- **类加载约定**：主类的字段、方法签名、静态初始化块里都不要出现版本相关类型；
  不要用 `new ModernHooks()`；版本实现类之间也不要互相直接引用。
- **26.x 起是年份版本号，别再假设版本字符串形如 `1.xx`。** 实测 26.2 下
  `matches(">=1.21.9")`、`matches(">=1.21.11")` 都为 true，而 `matches("<2.0")`
  为 false —— 纯语义版本比较会把 26.x 判成「大于所有 1.x」。写「1.x 时代」的分支
  时不要用 `<2.0` 之类上界；跨这种大版本优先用 `datapack:` / `resourcepack:` /
  `protocol:` / `world:`，或 `Compat` 的固定能力布尔，它们不依赖版本号的形状。
- **启动早期也可靠**：Mixin 插件阶段 Loader 可能还没注册 `minecraft` 容器，
  `GameVersion` 会改读 classpath 上的 `version.json` 取 id 兜底；
  所有查询首次调用后缓存，取不到数值时返回安全默认值（`UNKNOWN = -1` / false），不抛异常。
- `MinecraftVersions` 读的是 classpath 上的 `version.json`，若其它 jar 也带同名资源，
  优先取 id 与 Loader 报告一致的那份。

---

# 构建与安装

```bash
./gradlew build publishToMavenLocal     # 8 个 intermediary 版本节点
python tools/build_named26.py           # 26.2 官方名（named）适配 jar
python tools/selftest-number/run_selftest.py   # 数字输入校验的纯 Java 回归（不启动游戏）
```

第一条走 Stonecutter/Loom，按 `settings.gradle` 里的 8 个版本各编译一次，产物在
`versions/<版本>/build/libs/smile_libbox-1.0.4+<版本>.jar`。第二条不能用 Loom：
26.2 没有 intermediary / refmap，脚本从 `26.2.json` 精确构造 classpath（含
`fabric-api` 的 `META-INF/jars/*.jar` 子模块），另外从 `libraries/net/fabricmc/sponge-mixin`
兜底取注解 jar（启动器常把它的 `downloads.artifact` 留空，缺了就编译不了 mixin），
用 JDK 26 的 `javac --release 25` 直接对 26.2 客户端 jar 编译 `named26/src` 下的
适配层与三个 mixin，再以 1.20.1 jar 的纯 Java 部分为底拼出
`build/libs/smile_libbox-1.0.4+26.2.jar`；`named26/resources` 下的 mixin 配置会一起
写入 jar，并在 `fabric.mod.json` 的 `mixins` 里声明。可用环境变量
`MC_HOME` / `JAVAC` / `MC_VERSION` 覆盖路径。

> **改 `named26/src` 时注意**：这里没有 Stonecutter，`//? if ... {` 之类的条件分支
> 在 javac 眼里只是注释——代码会「编译通过但逻辑全没了」。曾经因此让重锤的致密等级
> 读取整段消失，所有重锤都被当成 0 级，永远切到背包里靠前的那把。脚本现在会在
> 编译前扫描 `named26/src`，发现条件注释直接报错退出；26.2 要用的分支请直接写出来。

根目录 `build/libs/` 下另有
`smile_libbox-1.0.4+generic.jar` 兜底。使用方按目标游戏版本引入对应坐标：

```groovy
repositories { mavenLocal() }
dependencies {
    modImplementation "smile_libbox:smile_libbox:1.0.4+1.20.1"
}
```

`fabric.mod.json` 里声明 `"depends": { "smile_libbox": "*" }`。运行时把
**与目标游戏版本一致的那一个**库 jar 放进 `mods/`；版本 jar 的
`depends.minecraft` 是 `~<版本>`（26.2 jar 另要求 `java: >=25`），装错会被 Loader
拒绝。`+26.2.jar` 的适配层是官方名描述符、class 版本 69，带三个按官方名写的 client mixin
（`smile_libbox.named26.mixins.json`，`compatibilityLevel: JAVA_25`）、不带 refmap；
`+generic.jar` 声明 `minecraft: "*"` 且不启用任何 client mixin，只在没有精确匹配的
版本时兜底：它是从 1.20.1 产物改元数据生成的，只有 `api` 包（零 MC 引用）可用，
`client.compat` 下的适配层仍是 intermediary 编译产物。**兜底 jar 不等于跨命名空间兼容**，
26.2 请用 `+26.2.jar`。

Smile's toolb 已按此接入（硬依赖），它自身是**单 jar**、声明 `minecraft: "*"`，
全部 34 个 class 的常量池零 `net/minecraft` 引用，只通过 `smile_libbox.api.ui.Ui`
门面访问游戏。它现在在 1.20.1~1.21.11（配 `+<版本>.jar`）与 26.2（配 `+26.2.jar`）
都能进主界面；详见「验证记录」。26.2 的启动、界面、音色、讲述人替换与四个 client
注入点（输入 / 状态条 / 聊天草稿）均已实机核对；进世界后的交互效果仍按
`VERIFY.md` 第 4 / 5 / 7 节人工走查。

# License

MIT，见 [LICENSE](LICENSE)。
