package com.projectbabel.integrations.registry.patchouli;

import com.projectbabel.integrations.registry.IntegrationContext;
import com.projectbabel.integrations.registry.ModIntegrationAdapter;
import com.projectbabel.integrations.registry.PreloadMode;
import com.projectbabel.integrations.books.patchouli.PatchouliBookPreloader;
import com.projectbabel.integrations.books.patchouli.PatchouliReloadHelper;

import java.util.List;

/** Adapter for Patchouli book preloading and screen reload. */
public final class PatchouliAdapter implements ModIntegrationAdapter {

    @Override
    public String id() {
        return "patchouli";
    }

    @Override
    public String displayName() {
        return "Patchouli";
    }

    @Override
    public List<String> modIds() {
        return List.of("patchouli");
    }

    @Override
    public void bootstrap(IntegrationContext context) {
    }

    @Override
    public void resetRuntimeState() {
        PatchouliBookPreloader.resetPreloadState();
    }

    @Override
    public void preloadForScreen(Object screen, PreloadMode mode) {
        if (screen == null) return;
        PatchouliBookPreloader.preloadBookForScreen(screen);
    }

    @Override
    public void refreshOpenUi(String reason) {
        PatchouliReloadHelper.requestReload(reason);
    }
}
