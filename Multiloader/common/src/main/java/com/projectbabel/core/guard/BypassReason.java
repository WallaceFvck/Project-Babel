package com.projectbabel.core.guard;

/** Why a component/text should bypass the generic translation hooks briefly. */
public enum BypassReason {
    /** Component rebuilt by Project Babel and already suitable for rendering. */
    GENERATED_TRANSLATION,

    /** Text produced by cache/output guard and must not re-enter the pipeline. */
    KNOWN_OUTPUT,

    /** Custom/user-authored text that should survive generic hooks unchanged. */
    USER_CUSTOM_TEXT,

    /** Compatibility fallback for old TranslationSkipRegistry call sites. */
    LEGACY
}
