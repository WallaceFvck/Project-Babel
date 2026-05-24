package com.projectbabel.integrations.registry;

import com.projectbabel.ProjectBabelCommon;

import java.util.List;

/**
 * Standard contract for optional mod integrations.
 *
 * The first pass keeps legacy preloaders/reload helpers intact and wraps them
 * behind this adapter surface. Future phases can move collectors and refreshers
 * into these modules without changing cache/pipeline callers again.
 */
public interface ModIntegrationAdapter {

    /** Stable internal id used by Project Babel, not necessarily a loader mod id. */
    String id();

    /** Human-readable name for logs/debug screens. */
    String displayName();

    /** mod ids that activate this adapter. */
    List<String> modIds();

    default boolean isLoaded() {
        try {
            for (String modId : modIds()) {
                if (modId != null && !modId.isBlank()
                    && ProjectBabelCommon.platform().mods().isLoaded(modId)) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    default void bootstrap(IntegrationContext context) {
    }

    /** Clears only runtime/preload state owned by this integration. */
    default void resetRuntimeState() {
    }

    /** Starts world-level/background preload when the integration supports it. */
    default void requestWorldPreload() {
    }

    /** Warm text for the currently opening screen/root object. */
    default void preloadForScreen(Object screen, PreloadMode mode) {
    }

    /** Refreshes the currently open UI if this adapter recognizes it. */
    default void refreshOpenUi(String reason) {
    }

    /** Optional collector hook for later migration away from reflection-heavy preloaders. */
    default List<TranslationSource> collectSources(Object root) {
        return List.of();
    }
}
