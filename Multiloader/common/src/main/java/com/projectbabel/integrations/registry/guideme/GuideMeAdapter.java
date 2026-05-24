package com.projectbabel.integrations.registry.guideme;

import com.projectbabel.integrations.registry.IntegrationContext;
import com.projectbabel.integrations.registry.ModIntegrationAdapter;
import com.projectbabel.integrations.registry.PreloadMode;
import com.projectbabel.integrations.books.guideme.GuideMePreloader;
import com.projectbabel.integrations.books.guideme.GuideMeReloadHelper;

import java.util.List;

/** Adapter for GuideME / AE2 Guide markdown preloading. */
public final class GuideMeAdapter implements ModIntegrationAdapter {

    @Override
    public String id() {
        return "guideme";
    }

    @Override
    public String displayName() {
        return "GuideME / AE2 Guide";
    }

    @Override
    public List<String> modIds() {
        return List.of("guideme");
    }

    @Override
    public void bootstrap(IntegrationContext context) {
        if (context.shouldTranslateMods()) {
            requestWorldPreload();
        }
    }

    @Override
    public void resetRuntimeState() {
        GuideMePreloader.reset();
    }

    @Override
    public void requestWorldPreload() {
        GuideMePreloader.requestWorldPreload();
    }

    @Override
    public void preloadForScreen(Object screen, PreloadMode mode) {
        requestWorldPreload();
    }

    @Override
    public void refreshOpenUi(String reason) {
        GuideMeReloadHelper.requestRefresh(reason);
    }
}
