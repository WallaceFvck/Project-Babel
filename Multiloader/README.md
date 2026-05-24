# Project Babel Multiloader

This is the active Project Babel workspace. It uses Gradle multi-project builds with Architectury Loom.

## Modules

| Module | Purpose |
| --- | --- |
| `common` | Shared translation API, pipeline, cache, dictionary, engines, scheduler, UI, vanilla mixins, and loader-neutral integrations |
| `forge` | Forge entrypoint, Forge config bridge, Forge events, Forge metadata, and Forge-specific compat mixins |
| `fabric` | Fabric entrypoint, Fabric config bridge, Fabric events, Fabric metadata, and Fabric-specific compat mixins |

## Targets

| Loader | Minecraft | Loader version |
| --- | --- | --- |
| Forge | 1.20.1 | 47.2.0 |
| Fabric | 1.20.1 | Fabric Loader 0.16.14, Fabric API 0.92.9+1.20.1 |

Project metadata and target versions live in `gradle.properties`.

Minecraft version lines are managed with Git branches, not duplicate folders. The current 1.20.1 line is represented by `main` and `mc/1.20.1`; future ports should use branches such as `mc/1.21.1`.

See `../docs/VERSIONING.md` before creating a new Minecraft version line.

## Commands

Validate architecture boundaries:

```powershell
.\gradlew.bat validateArchitecture
```

Compile modules:

```powershell
.\gradlew.bat :common:compileJava
.\gradlew.bat :forge:compileJava
.\gradlew.bat :fabric:compileJava
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

Run the standalone architecture checker:

```powershell
python scripts\validate_architecture.py
```

## Outputs

```text
forge/build/libs/
fabric/build/libs/
```

Do not commit generated outputs or local runtime data.

## Architecture Rule

`common` owns behavior. `forge` and `fabric` only connect that behavior to their loaders. If logic can be shared, it belongs in `common`.

For the full rules, read [ARCHITECTURE_GUIDE.md](ARCHITECTURE_GUIDE.md).
