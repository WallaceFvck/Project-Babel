# Versioning Strategy

Project Babel keeps one active `Multiloader/` workspace per Git branch. Minecraft versions are managed by branches, not by duplicating the full source tree into folders.

## Decision

Use this structure in every version branch:

```text
Project Babel/
  Multiloader/
    common/
    forge/
    fabric/
    build.gradle
    settings.gradle
    gradle.properties
  docs/
```

Do not create layouts like:

```text
Versions/
  1.20.1/Multiloader/
  1.21.1/Multiloader/
```

Duplicating the workspace inside one branch makes fixes harder to backport and increases the chance that loader behavior diverges silently.

## Branch Policy

Use `main` as the default line for the currently maintained stable target.

Use one Minecraft maintenance branch per target:

```text
mc/1.20.1
mc/1.21.1
mc/1.21.5
```

The current active target is Minecraft `1.20.1`. The `mc/1.20.1` branch should point at the same line as `main` while 1.20.1 is the primary release target.

When a newer Minecraft version becomes primary, move `main` forward through a normal merge or PR from that `mc/<version>` branch. Keep older `mc/<version>` branches for maintenance fixes.

## What Changes Per Version Branch

Version-specific values live mainly in:

```text
Multiloader/gradle.properties
Multiloader/settings.gradle
Multiloader/build.gradle
Multiloader/common/build.gradle
Multiloader/forge/build.gradle
Multiloader/fabric/build.gradle
```

For a new Minecraft port, update the version properties together:

```properties
minecraft_version=
minecraft_version_range=
fabric_minecraft_version_range=
java_version=
forge_version=
forge_loader_version_range=
forge_version_range=
fabric_loader_version=
fabric_loader_version_range=
fabric_api_version=
```

Then update source code, mixins, access wideners, metadata, and integration compatibility for that Minecraft version.

## Loader Policy

Forge and Fabric stay in the same `mc/<version>` branch when they target the same Minecraft version.

Use temporary loader-specific branches only when a port is incomplete:

```text
forge/1.21.1-port
fabric/1.21.1-port
neoforge/1.21.1-port
```

Merge those back into `mc/<version>` once the loader builds and passes validation.

NeoForge should become a `Multiloader/neoforge/` module when the port starts. Do not create a separate top-level `NeoForge/` source tree.

## Tags And Releases

Use tags that identify loader, Minecraft version, and mod version:

```text
forge-1.20.1-v1.0.0
fabric-1.20.1-v1.0.0
neoforge-1.21.1-v1.0.0
```

Attach final jars from:

```text
Multiloader/forge/build/libs/
Multiloader/fabric/build/libs/
Multiloader/neoforge/build/libs/
```

Do not attach or commit the full `build/` directories.

## Backport Flow

For bug fixes that apply to multiple Minecraft versions:

1. Fix the bug in the newest active development branch.
2. Cherry-pick the fix into older `mc/<version>` branches.
3. Resolve mapping/API differences per branch.
4. Run validation in each affected branch.
5. Tag releases per loader and Minecraft version.

Example:

```powershell
git checkout mc/1.20.1
git cherry-pick <fix-commit>
cd Multiloader
.\gradlew.bat validateArchitecture
.\gradlew.bat build
```

## Required Validation

Run these from `Multiloader/` before pushing a version port or backport:

```powershell
.\gradlew.bat validateArchitecture
.\gradlew.bat :common:compileJava
.\gradlew.bat :fabric:compileJava
.\gradlew.bat :forge:compileJava
.\gradlew.bat build
```

When NeoForge exists, add its compile/build tasks to the validation list.
