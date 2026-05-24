package com.projectbabel.integrations.registry;

/**
 * Defines how aggressively an integration may warm its cache for a UI object.
 */
public enum PreloadMode {
    ASYNC,
    BLOCKING_OPEN,
    BACKGROUND_WORLD
}
