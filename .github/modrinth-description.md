A prerequisite library for Fabric mods. It answers two questions in one place — *which game version am I on, and what does it support?* and *how do I touch the screens, key mappings, input events, narrator and contextual bar that Minecraft keeps changing between versions?* — with the answer compiled into the library instead of into your mod.

**Smile's Lib Box does nothing on its own.** No blocks, no items, no screen of its own. You install it because another mod lists it as a dependency, and then it just sits there making that mod work.

## Pick the jar for your game version

This library ships **one jar per game version**. A jar only loads on the version it was compiled against (`depends.minecraft` is `~<version>`), so the loader refuses a mismatched jar up front instead of crashing later in-game.

| Minecraft | File to download | Java |
| --- | --- | --- |
| 1.20.1 | `smile_libbox-<version>+1.20.1.jar` | 17+ |
| 1.20.4 | `smile_libbox-<version>+1.20.4.jar` | 17+ |
| 1.20.6 | `smile_libbox-<version>+1.20.6.jar` | 21+ |
| 1.21.1 | `smile_libbox-<version>+1.21.1.jar` | 21+ |
| 1.21.4 | `smile_libbox-<version>+1.21.4.jar` | 21+ |
| 1.21.5 | `smile_libbox-<version>+1.21.5.jar` | 21+ |
| 1.21.8 | `smile_libbox-<version>+1.21.8.jar` | 21+ |
| 1.21.11 | `smile_libbox-<version>+1.21.11.jar` | 21+ |

Drop the matching file into `mods/`. [Fabric API](https://modrinth.com/mod/fabric-api) is required; nothing else needs configuring, and there is no version-agnostic "universal" jar on purpose — see *For mod authors* below.

The library works on both the client and a dedicated server. Servers only need it if a mod on the server depends on it.

## For mod authors

Depending on it is the point: **your mod can stay a single jar across 1.20.1 – 1.21.11**, without shipping eight builds or writing Stonecutter conditionals.

```groovy
dependencies { modImplementation "smile_libbox:smile_libbox:<version>+1.21.1" }
```

and in your `fabric.mod.json`, `"depends": { "smile_libbox": "*" }`.

What it gives you:

- **`GameVersion` / `MinecraftVersions`** — game version, data-pack and resource-pack format, protocol and world-data version, read from `version.json` with the JDK alone (zero Minecraft dependencies). The pre-1.21.9 `pack_version` shape and the newer `*_major` / `*_minor` shape are normalised into major + minor, so you never branch on it.
- **`Compat`** — a fixed boolean per capability that genuinely changes compilation signatures: `fourArgBackground()`, `dataComponents()`, `resourceLocationFactory()`, `narratorVolume()`, `eventBasedInput()`, `contextualBar()`.
- **`Conditional`** — one expression language for all of it: `">=1.21.9 & mod:vanillabackport"`, `"datapack:>=15.0"`, `"cap:eventBasedInput"`, `"class:net.minecraft.client.KeyMapping$Category"`. Capability and data-version checks track what you actually call more closely than raw version numbers, which matters on modpacks that backport features.
- **`ModPresence`** — is a mod loaded, does a class exist.
- **`smile_libbox.api.ui`** — a plain-Java screen facade (`Ui`, `UiScreen`, `UiCanvas`, `UiHost`, `ConfigSpec`, `KeyRef`). Nothing under that package references a `net.minecraft.*` type, verified against the class constant pools, which is what keeps *your* main jar free of game types.
- **`InputEvents` / `BarEvents` / `ChatDraftEvents` / `World` / `Packets`** — client input, the bar above the hotbar, the chat draft, world state and outbound container actions, each behind an interface that survives version changes.

The per-version adapter layer and mixins live inside the jar and are compiled against that version's Minecraft, refmaps included; your code never sees them.

## 中文说明

Fabric 模组的**前置库**：把「当前是哪个游戏版本、它有哪些能力」和「屏幕、键位、输入事件、讲述人、上下文条这些每版都在变的接口」收敛成一套固定 API，变化的部分编译进库里，而不是你的模组里。

**它本身不提供任何内容**，没有方块、物品、界面 —— 是别的模组声明依赖它，你才需要装。

按游戏版本**各有一个 jar**（1.20.1 / 1.20.4 / 1.20.6 / 1.21.1 / 1.21.4 / 1.21.5 / 1.21.8 / 1.21.11），装错版本会被 Loader 直接拒绝，所以挑文件名里游戏版本对得上的那一个丢进 `mods/` 即可。需要 [Fabric API](https://modrinth.com/mod/fabric-api)。客户端与服务端都能用，服务端只在有模组依赖它时才需要。

对模组作者：依赖它之后，你的模组可以**一个 jar 通吃 1.20.1 – 1.21.11**，不用出八份构建，也不用写 Stonecutter 条件编译。

## Links

- [Source code](https://github.com/Smile-Wood/Smiles-Lib-Box)
- [中文 README](https://github.com/Smile-Wood/Smiles-Lib-Box/blob/main/README.zh-CN.md)
- [Issue tracker](https://github.com/Smile-Wood/Smiles-Lib-Box/issues)
