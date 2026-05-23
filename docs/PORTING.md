# Porting Strategy

Project Babel should support multiple Minecraft versions and loaders without mixing generated files or local environment data into source control.

## Current Layout

The repository root is only an organizer for loader folders:

```text
Forge/
  Project Babel 1.20.1 - 1.21/
NeoForge/
Fabric/
```

The active Forge build is:

- `Forge/Project Babel 1.20.1 - 1.21/src/main/java`: mod source code
- `Forge/Project Babel 1.20.1 - 1.21/src/main/resources`: mod metadata, assets, lang files, and mixin config
- `Forge/Project Babel 1.20.1 - 1.21/build.gradle`: ForgeGradle build for Minecraft 1.20.1
- `Forge/Project Babel 1.20.1 - 1.21/gradle.properties`: version and mod metadata

Do not commit `build/`, `.gradle/`, `.gradle-home/`, `.inspect/`, `run/`, or local deploy scripts.

## Branch Naming

Use one maintenance branch per loader and Minecraft version:

- `forge/1.20.1`
- `neoforge/1.20.1`
- `neoforge/1.21.1`
- `fabric/1.20.1`
- `fabric/1.21.1`

Keep `main` as the latest stable line or the default development line. Avoid mixing unrelated loader ports in the same branch unless the project is migrated to a multi-loader Gradle setup.

## Release Tags

Use tags that include loader, Minecraft version, and mod version:

- `forge-1.20.1-v1.0.0`
- `neoforge-1.21.1-v1.0.0`
- `fabric-1.21.1-v1.0.0`

GitHub Releases should attach only the final jar files from `build/libs/`, not the whole `build/` directory.

## When To Split Shared Code

Keep the root Forge project as-is until another loader actually exists. When the first Fabric or NeoForge port starts, migrate shared translation logic into a common module and keep loader-specific entrypoints, events, mixins, and metadata separate.

Recommended layout for each loader:

```text
Fabric/
  Project Babel 1.20.1 - 1.21/
  Project Babel 1.21.1 - 1.21.5/
NeoForge/
  Project Babel 1.20.1 - 1.21/
  Project Babel 1.21.1 - 1.21.5/
```

If code sharing becomes painful, migrate the loader/version folders to a multi-project Gradle setup with a shared `common` module. Do that only when the second loader port is real.
