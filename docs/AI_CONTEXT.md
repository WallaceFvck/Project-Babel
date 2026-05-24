# AI Maintenance Context

Read this before making structural changes to Project Babel.

## Repository Shape

Project Babel uses one Gradle multi-project workspace:

```text
Multiloader/
  common/
  forge/
  fabric/
```

Do not recreate old top-level loader folders such as:

```text
Forge/
Fabric/
NeoForge/
Versions/
```

Minecraft versions are represented by Git branches:

```text
main
mc/1.20.1
mc/1.21.1
mc/1.21.5
```

The branch changes version properties and source compatibility. The folder layout stays the same.

## Current Target

Current active target:

```text
Minecraft: 1.20.1
Java: 17
Loaders: Forge and Fabric
Workspace: Multiloader/
```

Version values live in:

```text
Multiloader/gradle.properties
```

## Architecture Rules

`common` owns behavior:

- translation API
- pipeline
- cache
- dictionary and universal terms
- engines
- scheduler
- UI shared by loaders
- vanilla mixins
- loader-neutral integrations

`forge` and `fabric` are thin adapters:

- entrypoints
- loader events
- config bridges
- platform services
- metadata
- loader-specific compat mixins

Do not duplicate engines, cache, scheduler, dictionary, pipeline, or vanilla mixins in loader modules.

## Versioning Rules

Do not duplicate `Multiloader/` per Minecraft version inside one branch.

Correct:

```text
branch mc/1.20.1 -> Multiloader/
branch mc/1.21.1 -> Multiloader/
```

Incorrect:

```text
main -> Versions/1.20.1/Multiloader/
main -> Versions/1.21.1/Multiloader/
```

Create a new Minecraft version line with:

```powershell
git checkout main
git checkout -b mc/<minecraft-version>
```

Then update `Multiloader/gradle.properties`, loader build files, mappings, metadata, mixins, and code for that version.

## Required Validation

Run from `Multiloader/`:

```powershell
.\gradlew.bat validateArchitecture
.\gradlew.bat build
```

If only documentation changed, Git status/diff review is enough.

## Reference Docs

- `docs/VERSIONING.md`: branch and release policy
- `docs/PORTING.md`: Minecraft and loader porting process
- `Multiloader/ARCHITECTURE_GUIDE.md`: full architectural constraints
- `Multiloader/README.md`: build commands and module summary
