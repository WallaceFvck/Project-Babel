package com.projectbabel.integrations.registry.modonomicon;

import com.projectbabel.integrations.registry.ModIntegrationAdapter;
import com.projectbabel.integrations.registry.PreloadMode;
import com.projectbabel.integrations.registry.UiRefreshService;
import com.projectbabel.integrations.books.modonomicon.ModonomiconBookPreloader;

import java.util.List;

/** Adapter for Modonomicon markdown preloading. */
public final class ModonomiconAdapter implements ModIntegrationAdapter {

    @Override
    public String id() {
        return "modonomicon";
    }

    @Override
    public String displayName() {
        return "Modonomicon";
    }

    @Override
    public List<String> modIds() {
        return List.of("modonomicon");
    }

    @Override
    public void resetRuntimeState() {
        ModonomiconBookPreloader.resetPreloadState();
    }

    @Override
    public void preloadForScreen(Object screen, PreloadMode mode) {
        if (screen == null) return;
        ModonomiconBookPreloader.preloadBookForScreen(screen);
    }

    @Override
    public void refreshOpenUi(String reason) {
        UiRefreshService.requestCurrentScreenRefresh(
            displayName(),
            reason,
            "modonomicon"
        );
    }
}
