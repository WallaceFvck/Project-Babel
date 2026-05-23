[![Downloads](https://img.shields.io/github/downloads/WallaceFvck/Project-Babel/total?style=for-the-badge&label=downloads)](https://github.com/WallaceFvck/Project-Babel/releases)

# Project Babel

<div align="center">
    <img src="https://raw.githubusercontent.com/WallaceFvck/Project-Babel/main/docs/projectbabel.png" width="320"/>
</div>
<p align="center"><em>A Minecraft client-side translation mod for modded gameplay</em></p>

## Why I made it

Modded Minecraft has a huge amount of text spread across tooltips, GUIs, quests, books, ponder scenes, overlays, advancements, and mod-specific screens. Many modpacks are hard to play when that text is not available in your language.

Project Babel was made to translate those client-side texts in real time while keeping the game usable. The goal is to make large modpacks easier to understand without manually editing every language file in every mod.

The current maintained build targets Forge for Minecraft 1.20.1. Fabric and NeoForge versions are planned and the repository is already organized for multiple loaders and Minecraft version ranges.

---

## Features

Core translation:
- Real-time client-side text translation
- Translation cache to avoid repeating the same requests
- Language detection and filtering for text that should not be translated
- Translation overlay and cache screen
- Support code for multiple translation engines

Mod integration coverage:
- Tooltips and item text
- Chat components
- Advancement text
- Book screens
- Patchouli books
- Modonomicon books
- FTB Quests screens and quest objects
- Create Ponder text
- GuideME text
- Jade display text
- Applied Energistics 2 screens and tooltips
- Refined Storage screens
- Enchantment description text

Repository structure:
- Separate loader folders for Forge, NeoForge, and Fabric
- Version-range folders inside each loader
- Build outputs, Gradle caches, local runtime data, and machine-specific scripts ignored by Git

---

## Installation

Download the latest release from:

<p align="center">
  <a href="https://github.com/WallaceFvck/Project-Babel/releases/latest">
    <img src="https://github.com/machiav3lli/oandbackupx/blob/034b226cea5c1b30eb4f6a6f313e4dadcbb0ece4/badge_github.png" alt="Download from GitHub" height="75">
  </a>
</p>

Install the `.jar` file into your Minecraft instance `mods` folder.

Current build status:

| Loader | Minecraft | Status |
| --- | --- | --- |
| Forge | 1.20.1 | Active |
| NeoForge | TBD | Planned |
| Fabric | TBD | Planned |

---

## Building From Source

Requirements:
- Java 17
- Git
- Internet access for the first Gradle dependency download

Clone the repository:

```powershell
git clone https://github.com/WallaceFvck/Project-Babel.git
cd "Project-Babel"
```

Build the active Forge version:

```powershell
cd "Forge\Project Babel 1.20.1 - 1.21"
.\gradlew.bat build
```

The compiled mod jar is written to:

```text
Forge/Project Babel 1.20.1 - 1.21/build/libs/
```

Useful validation command:

```powershell
.\gradlew.bat build
```

---

## Repository Layout

```text
Project Babel/
  Forge/
    Project Babel 1.20.1 - 1.21/
  NeoForge/
  Fabric/
  docs/
```

The repository root only contains shared repository files. Loader-specific Gradle projects live inside their own loader and version folders.

See [docs/PORTING.md](docs/PORTING.md) for the version and loader porting strategy.

---

## Support the project

You can support the project by starring the repository, reporting issues, testing releases, and sharing it with other modded Minecraft players.

## License

This project is licensed under the [MIT License](LICENSE).
