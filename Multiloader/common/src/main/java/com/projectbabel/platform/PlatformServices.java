package com.projectbabel.platform;

/** Aggregates all platform-specific services required by common code. */
public interface PlatformServices {
    BabelConfigView config();

    ModLookup mods();

    PathsProvider paths();

    ClientExecutor clientExecutor();
}
