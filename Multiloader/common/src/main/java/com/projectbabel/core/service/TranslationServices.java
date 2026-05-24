package com.projectbabel.core.service;

import com.projectbabel.api.TranslationService;

/** Central service registry for Project Babel core services. */
public final class TranslationServices {
    private static final TranslationService TRANSLATION = new ProjectBabelTranslationService();

    private TranslationServices() {}

    public static TranslationService translation() {
        return TRANSLATION;
    }
}
