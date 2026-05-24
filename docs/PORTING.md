# Porting Strategy

Project Babel now uses a multi-project Gradle setup under `Multiloader/`. The current active loaders are Forge and Fabric for Minecraft 1.20.1.

## Current Layout

```text
Multiloader/
  common/
  forge/
  fabric/
  scripts/
  build.gradle
  settings.gradle
  gradle.properties
```

The root repository is only for shared docs, license, Git metadata, and the `Multiloader/` workspace. Loader-specific builds should stay inside the multi-project workspace.

## Module Responsibilities

`common` is the source of truth. It owns:

- translation API and service facade
- pipeline, cache, dictionary, glossary, and skip rules
- translation engines
- scheduler and preload acceleration
- UI shared by loaders
- vanilla Minecraft mixins
- loader-neutral mod integrations
- shared access widener and common mixin config

`forge` and `fabric` are thin adapters. They own:

- loader entrypoints
- native events and lifecycle hooks
- native config bridges
- keybind registration
- resource reload bridge
- loader metadata
- loader-specific compat mixins

Do not duplicate cache, engines, scheduler, dictionary, pipeline, or vanilla mixins inside loader modules.

## Version Targets

Current target values are centralized in `Multiloader/gradle.properties`:

| Property | Current value |
| --- | --- |
| `minecraft_version` | `1.20.1` |
| `java_version` | `17` |
| `enabled_platforms` | `fabric,forge` |
| `forge_version` | `47.2.0` |
| `fabric_loader_version` | `0.16.14` |
| `fabric_api_version` | `0.92.9+1.20.1` |

When porting to another Minecraft version, create a dedicated branch first and update all version properties together. Do not mix unrelated Minecraft targets in one branch.

Suggested branch names:

- `multiloader/1.20.1`
- `multiloader/1.21.1`
- `fabric/1.21.1`
- `forge/1.21.1`
- `neoforge/1.21.1`

## Adding NeoForge

NeoForge is planned, but not currently part of `enabled_platforms`.

When starting the NeoForge port:

1. Add a `neoforge` module under `Multiloader/`.
2. Include it in `settings.gradle`.
3. Add `neoforge` to `enabled_platforms`.
4. Keep NeoForge code as a thin adapter like `forge` and `fabric`.
5. Move reusable behavior into `common`, not into the NeoForge module.
6. Add metadata, config bridge, platform services, events, and compat mixins only where needed.
7. Extend validation tasks so NeoForge follows the same boundaries.

## Release Tags

Use release tags that include loader, Minecraft version, and mod version:

- `forge-1.20.1-v1.0.0`
- `fabric-1.20.1-v1.0.0`
- `neoforge-1.21.1-v1.0.0`

GitHub Releases should attach only final loader jars from `Multiloader/<loader>/build/libs/`, not the whole `build/` directory.

## Required Validation

Run these from `Multiloader/` before considering a port complete:

```powershell
.\gradlew.bat validateArchitecture
.\gradlew.bat :common:compileJava
.\gradlew.bat :fabric:compileJava
.\gradlew.bat :forge:compileJava
.\gradlew.bat build
```

The Python architecture checker can also be run directly:

```powershell
python scripts\validate_architecture.py
```

## Do Not Commit

Keep these out of Git:

- `build/`
- `.gradle/`
- `.gradle-home/`
- `.local/`
- `.inspect/`
- `run/`
- generated jars outside release assets
- machine-specific deploy scripts

These are already covered by the repository `.gitignore`.
