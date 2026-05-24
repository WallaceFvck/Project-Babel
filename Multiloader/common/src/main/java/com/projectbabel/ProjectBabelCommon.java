package com.projectbabel;

import com.projectbabel.platform.BabelConfigView;
import com.projectbabel.platform.PlatformServices;
import com.projectbabel.platform.reload.ProjectBabelReloadBus;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Objects;

/**
 * Platform-neutral bootstrap holder for Project Babel.
 *
 * Platform modules register their services here before any common translation,
 * cache, dictionary or engine code is initialized.
 */
public final class ProjectBabelCommon {
    public static final String MOD_ID = "projectbabel";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    private static PlatformServices platform;

    private ProjectBabelCommon() {}

    public static void init(PlatformServices services) {
        platform = Objects.requireNonNull(services, "services");
        ProjectBabelReloadBus.initCommonListeners();
    }

    public static PlatformServices platform() {
        if (platform == null) {
            throw new IllegalStateException("Project Babel common has not been initialized");
        }
        return platform;
    }

    public static BabelConfigView config() {
        return platform().config();
    }

    public static boolean isInitialized() {
        return platform != null;
    }
}
