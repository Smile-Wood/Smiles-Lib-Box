First public release of Smile's Lib Box, a prerequisite library that lets one Fabric mod run on 1.20.1 through 1.21.11 without shipping a build per version.

**This mod adds nothing by itself.** Install it only when another mod lists it as a dependency.

## What's in it

- **One jar per game version** — 1.20.1, 1.20.4, 1.20.6, 1.21.1, 1.21.4, 1.21.5, 1.21.8, 1.21.11. Download the one matching the version you play; `depends.minecraft` is `~<version>`, so a mismatched jar is rejected by the loader instead of failing at runtime.
- **Version queries without touching Minecraft** — `GameVersion` and `MinecraftVersions` read `version.json` with the JDK alone and normalise both pack-version shapes (the pre-1.21.9 `{resource, data}` object and the newer `*_major` / `*_minor` object) into major + minor.
- **Capability checks instead of version checks** — `Compat.fourArgBackground()`, `dataComponents()`, `resourceLocationFactory()`, `narratorVolume()`, `eventBasedInput()`, `contextualBar()`.
- **One condition language** — `Conditional.matches(">=1.21.9 & mod:vanillabackport")`, plus `datapack:`, `resourcepack:`, `protocol:`, `world:`, `cap:`, `mod:`, `class:` prefixes.
- **A plain-Java UI layer** — `smile_libbox.api.ui` (`Ui`, `UiScreen`, `UiCanvas`, `UiHost`, `ConfigSpec`, `KeyRef`) references no `net.minecraft.*` type at all, verified against the class constant pools. That is what keeps a dependent mod's main jar free of game types.
- **Injection points behind interfaces** — client input, the bar above the hotbar, the chat draft, world state, outbound container packets.

## Requirements

- [Fabric API](https://modrinth.com/mod/fabric-api) — required
- Java 17 for 1.20.1 / 1.20.4, Java 21 for 1.20.6 and newer
- Works on client and dedicated server; a server only needs it if a mod on the server depends on it

## For mod authors

```groovy
dependencies { modImplementation "smile_libbox:smile_libbox:1.0.26+1.21.1" }
```

and `"depends": { "smile_libbox": "*" }` in `fabric.mod.json`. Full API reference: [README](https://github.com/Smile-Wood/Smiles-Lib-Box).

Note there is deliberately no version-agnostic "universal" jar: the adapter layer and mixins are compiled against a specific version's Minecraft, refmaps included, and a repackaged fallback would only expose the pure-Java part while silently loading no adapters.
