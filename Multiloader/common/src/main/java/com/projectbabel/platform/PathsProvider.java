package com.projectbabel.platform;

import java.nio.file.Path;

/** Loader-neutral access to Project Babel filesystem locations. */
public interface PathsProvider {
    Path gameDir();

    Path configDir();
}
