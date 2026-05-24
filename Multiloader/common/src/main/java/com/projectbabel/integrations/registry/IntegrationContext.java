package com.projectbabel.integrations.registry;

import com.projectbabel.ProjectBabelCommon;
import com.projectbabel.core.text.LanguageDetector;
import org.apache.logging.log4j.Logger;

/**
 * Shared context passed to integrations during bootstrap/runtime callbacks.
 */
public final class IntegrationContext {

    private final Logger logger;

    private IntegrationContext(Logger logger) {
        this.logger = logger;
    }

    public static IntegrationContext client() {
        return new IntegrationContext(ProjectBabelCommon.LOGGER);
    }

    public Logger logger() {
        return logger;
    }

    public String targetLanguage() {
        return LanguageDetector.getTargetLanguageForApi();
    }

    public boolean shouldTranslateMods() {
        return LanguageDetector.shouldModBeActive();
    }
}
