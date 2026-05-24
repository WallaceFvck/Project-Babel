#!/usr/bin/env python3
"""
Architecture validation for Project Babel multi-loader.

Checks performed:
1. :common must not import platform APIs or platform namespaces.
2. Java package declarations must match src/main/java paths.
3. Migrated legacy namespaces must not reappear.
4. Forge subproject must declare loom.platform=forge.
5. SpongePowered Maven must be available for the Mixin annotation processor.

Usage:
  python scripts/validate_architecture.py
"""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MODULES = ("common", "fabric", "forge")

COMMON_BANNED_TOKENS = (
    "net.fabricmc.",
    "net.minecraftforge.",
    "com.projectbabel.fabric.",
    "com.projectbabel.forge.",
    "FabricLoader",
    "ModList",
    "AutoTranslateConfig",
)

LEGACY_NAMESPACES = (
    "com.projectbabel.translation.",
    "com.projectbabel.config.",
    "com.projectbabel.event.",
    "com.projectbabel.client.",
    "com.projectbabel.minecraft.event.",
    "com.projectbabel.screen.",
    "com.projectbabel.overlay.",
)

PACKAGE_RE = re.compile(r"(?m)^\s*package\s+([a-zA-Z0-9_.]+)\s*;")
PLACEHOLDER_RE = re.compile(r"\$\{([^}]+)\}")
METADATA_FILES = (
    Path("fabric/src/main/resources/fabric.mod.json"),
    Path("forge/src/main/resources/META-INF/mods.toml"),
)


def load_properties_file(path: Path) -> dict[str, str]:
    props: dict[str, str] = {}
    if not path.exists():
        return props
    for raw_line in path.read_text(encoding="utf-8", errors="replace").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        props[key.strip()] = value.strip()
    return props


def java_files(module: str):
    root = ROOT / module / "src" / "main" / "java"
    if not root.exists():
        return
    for path in root.rglob("*.java"):
        yield root, path


def check_common_boundaries() -> list[str]:
    violations: list[str] = []
    for _, path in java_files("common") or []:
        content = path.read_text(encoding="utf-8", errors="replace")
        for token in COMMON_BANNED_TOKENS:
            if token in content:
                violations.append(f"{path.relative_to(ROOT)}: contains '{token}'")
    return violations


def check_java_packages() -> list[str]:
    violations: list[str] = []
    for module in MODULES:
        for java_root, path in java_files(module) or []:
            content = path.read_text(encoding="utf-8", errors="replace")
            match = PACKAGE_RE.search(content)
            if not match:
                violations.append(f"{path.relative_to(ROOT)}: missing package declaration")
                continue
            declared = match.group(1)
            expected = Path(*declared.split(".")) / path.name
            actual = path.relative_to(java_root)
            if actual != expected:
                violations.append(
                    f"{path.relative_to(ROOT)}: declares '{declared}', expected '{expected.as_posix()}', actual '{actual.as_posix()}'"
                )
    return violations


def check_legacy_namespaces() -> list[str]:
    violations: list[str] = []
    for module in MODULES:
        for _, path in java_files(module) or []:
            content = path.read_text(encoding="utf-8", errors="replace")
            for namespace in LEGACY_NAMESPACES:
                if namespace in content:
                    violations.append(f"{path.relative_to(ROOT)}: contains legacy namespace '{namespace}'")
    return violations




def load_gradle_properties() -> dict[str, str]:
    return load_properties_file(ROOT / "gradle.properties")


def check_loom_platform_files() -> list[str]:
    violations: list[str] = []
    forge_props_path = ROOT / "forge" / "gradle.properties"
    forge_props = load_properties_file(forge_props_path)
    if forge_props.get("loom.platform") != "forge":
        violations.append("forge/gradle.properties: expected loom.platform=forge")
    return violations


def check_resource_placeholders() -> list[str]:
    violations: list[str] = []
    props = load_gradle_properties()
    for relative_path in METADATA_FILES:
        path = ROOT / relative_path
        if not path.exists():
            continue
        content = path.read_text(encoding="utf-8", errors="replace")
        missing = sorted(set(PLACEHOLDER_RE.findall(content)) - set(props))
        for key in missing:
            violations.append(f"{relative_path}: undefined placeholder '{key}'")

        if relative_path.suffix == ".json" and not missing:
            expanded = PLACEHOLDER_RE.sub(lambda match: props[match.group(1)], content)
            try:
                json.loads(expanded)
            except json.JSONDecodeError as exc:
                violations.append(f"{relative_path}: expanded JSON is invalid: {exc}")
    return violations



def check_common_mixin_infrastructure() -> list[str]:
    violations: list[str] = []
    for rel in (
        "fabric/src/main/java/com/projectbabel/mixin/vanilla",
        "forge/src/main/java/com/projectbabel/mixin/vanilla",
    ):
        path = ROOT / rel
        if path.exists() and any(path.rglob("*.java")):
            violations.append(f"{rel}: vanilla mixins must live in common")

    for rel in (
        "common/src/main/resources/projectbabel-common.mixins.json",
        "common/src/main/resources/projectbabel.accesswidener",
        "common/src/main/resources/architectury.common.json",
    ):
        if not (ROOT / rel).exists():
            violations.append(f"missing {rel}")

    common_build = (ROOT / "common/build.gradle").read_text(encoding="utf-8", errors="replace")
    if 'implementation "com.google.code.gson:gson' in common_build:
        violations.append("common/build.gradle must use compileOnly for Gson, not implementation")

    return violations



def check_sponge_repository() -> list[str]:
    violations: list[str] = []
    token = "repo.spongepowered.org/repository/maven-public"
    settings = (ROOT / "settings.gradle").read_text(encoding="utf-8", errors="replace") if (ROOT / "settings.gradle").exists() else ""
    build = (ROOT / "build.gradle").read_text(encoding="utf-8", errors="replace") if (ROOT / "build.gradle").exists() else ""
    if token not in settings and token not in build:
        violations.append("missing SpongePowered Maven repository required for org.spongepowered:mixin:<version>:processor")
    return violations


def check_gradle_environment() -> list[str]:
    violations: list[str] = []

    wrapper = ROOT / "gradle" / "wrapper" / "gradle-wrapper.properties"
    wrapper_text = wrapper.read_text(encoding="utf-8", errors="replace") if wrapper.exists() else ""
    if "gradle-8.11.1-bin.zip" not in wrapper_text:
        violations.append("gradle/wrapper/gradle-wrapper.properties: expected gradle-8.11.1-bin.zip")

    settings = (ROOT / "settings.gradle").read_text(encoding="utf-8", errors="replace") if (ROOT / "settings.gradle").exists() else ""
    if "com.github.johnrengelman.shadow" in settings:
        violations.append("settings.gradle: legacy com.github.johnrengelman.shadow plugin id must not be used")
    if "id 'com.gradleup.shadow' version '8.3.9'" not in settings:
        violations.append("settings.gradle: expected com.gradleup.shadow version 8.3.9")

    legacy_plugin_re = re.compile(r"(?m)^\s*id\s+['\"]com\.github\.johnrengelman\.shadow['\"]")
    for rel in ("build.gradle", "fabric/build.gradle", "forge/build.gradle"):
        path = ROOT / rel
        content = path.read_text(encoding="utf-8", errors="replace") if path.exists() else ""
        if legacy_plugin_re.search(content):
            violations.append(f"{rel}: legacy com.github.johnrengelman.shadow plugin id must not be used")

    return violations

def main() -> int:
    groups = {
        "Common boundary violations": check_common_boundaries(),
        "Package/path mismatches": check_java_packages(),
        "Legacy namespace violations": check_legacy_namespaces(),
        "Loom platform file violations": check_loom_platform_files(),
        "Resource placeholder violations": check_resource_placeholders(),
        "Common mixin infrastructure violations": check_common_mixin_infrastructure(),
        "Sponge repository violations": check_sponge_repository(),
        "Gradle environment violations": check_gradle_environment(),
    }

    failed = False
    for title, violations in groups.items():
        if not violations:
            continue
        failed = True
        print(f"\n{title}:", file=sys.stderr)
        for violation in violations:
            print(f"  - {violation}", file=sys.stderr)

    if failed:
        return 1

    print("Architecture validation passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
