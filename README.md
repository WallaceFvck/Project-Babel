[![Downloads](https://img.shields.io/github/downloads/WallaceFvck/Project-Babel/total?style=for-the-badge&label=downloads)](https://github.com/WallaceFvck/Project-Babel/releases)
![Minecraft](https://img.shields.io/badge/Minecraft-1.20.1-62B47A?style=for-the-badge)
![Loaders](https://img.shields.io/badge/Loaders-Forge%20%7C%20Fabric-4C8EDA?style=for-the-badge)
![Java](https://img.shields.io/badge/Java-17-E34F26?style=for-the-badge)

# Project Babel

<div align="center">
    <img src="https://raw.githubusercontent.com/WallaceFvck/Project-Babel/main/docs/projectbabel.png" width="320"/>
</div>
<p align="center"><em>A Minecraft client-side translation mod for modded gameplay</em></p>

## Why I made it

Modded Minecraft spreads important text across tooltips, GUIs, quests, books, ponder scenes, overlays, advancements, and mod-specific screens. Large packs become harder to play when that text is not available in your language.

Project Babel translates client-side text in real time while keeping rendering responsive. The goal is to make modpacks easier to understand without manually editing every language file in every mod.

The project has been migrated to a Gradle multi-project layout using Architectury Loom. Shared logic lives in `common`, while `forge` and `fabric` stay as thin loader adapters.

---

## Status

| Module | Target | Status |
| --- | --- | --- |
| `common` | Minecraft 1.20.1 shared code | Active |
| `forge` | Forge 47.2.0 / Minecraft 1.20.1 | Active |
| `fabric` | Fabric Loader 0.16.x + Fabric API / Minecraft 1.20.1 | Active |
| NeoForge | Future port | Planned |

Current mod version: `1.0.0`.

---

## Features

Core translation:
- Real-time client-side text translation
- Translation cache with invalidation hooks
- Language detection, skip rules, and text filtering
- Glossary and universal-term preservation
- Translation scheduler, priorities, and preload acceleration
- Google Translate and Lingva engine support
- Cache UI and in-game translation overlay

Minecraft and mod coverage:
- Vanilla tooltips, chat, books, overlays, advancements, item stacks, and rendered text
- Patchouli, Modonomicon, and GuideME books
- FTB Quests and FTB Library UI text
- Create Ponder text and tooltips
- Jade display text
- Applied Energistics 2 screens and tooltips
- Refined Storage screens
- Enchantment description text

Architecture:
- `common` owns the API, pipeline, cache, dictionary, engines, scheduler, UI, vanilla mixins, and loader-neutral integrations
- `forge` owns Forge entrypoints, events, config bridge, metadata, and Forge-specific compat mixins
- `fabric` owns Fabric entrypoints, events, config bridge, metadata, and Fabric-specific compat mixins
- Architecture validation prevents platform APIs from leaking into shared code

---

## Installation

Download the latest release from:

<p align="center">
  <a href="https://github.com/WallaceFvck/Project-Babel/releases/latest">
    <img src="https://github.com/machiav3lli/oandbackupx/blob/034b226cea5c1b30eb4f6a6f313e4dadcbb0ece4/badge_github.png" alt="Download from GitHub" height="75">
  </a>
</p>

Install the loader-specific `.jar` file into your Minecraft instance `mods` folder.

---

## Building From Source

Requirements:
- Java 17
- Git
- Internet access for the first Gradle dependency download

Clone the repository:

```powershell
git clone https://github.com/WallaceFvck/Project-Babel.git
cd "Project-Babel\Multiloader"
```

Validate the architecture:

```powershell
.\gradlew.bat validateArchitecture
```

Build all active modules:

```powershell
.\gradlew.bat build
```

Build one loader:

```powershell
.\gradlew.bat :forge:build
.\gradlew.bat :fabric:build
```

Output jars are written to:

```text
Multiloader/forge/build/libs/
Multiloader/fabric/build/libs/
```

---

## Repository Layout

```text
Project Babel/
  Multiloader/
    common/
    forge/
    fabric/
    scripts/
    build.gradle
    settings.gradle
    gradle.properties
  docs/
```

Important docs:
- [Multiloader README](Multiloader/README.md)
- [Architecture Guide](Multiloader/ARCHITECTURE_GUIDE.md)
- [Porting Strategy](docs/PORTING.md)
- [Versioning Strategy](docs/VERSIONING.md)
- [AI Maintenance Context](docs/AI_CONTEXT.md)

Build outputs, Gradle caches, local runtime folders, decompiled sources, and machine-specific scripts are intentionally ignored by Git.

---

## Support the project

You can support the project by starring the repository, reporting issues, testing releases, and sharing it with other modded Minecraft players.

## License

This project is licensed under the [MIT License](LICENSE).
