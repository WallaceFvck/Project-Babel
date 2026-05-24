package com.projectbabel.integrations.registry;

import com.projectbabel.ProjectBabelCommon;
import com.projectbabel.integrations.registry.create.CreatePonderAdapter;
import com.projectbabel.integrations.registry.ftbquests.FTBQuestsAdapter;
import com.projectbabel.integrations.registry.guideme.GuideMeAdapter;
import com.projectbabel.integrations.registry.modonomicon.ModonomiconAdapter;
import com.projectbabel.integrations.registry.patchouli.PatchouliAdapter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Central registry for optional mod integrations.
 *
 * It removes hard-coded integration calls from cache/pipeline classes and gives
 * every optional mod the same lifecycle surface: bootstrap, reset, preload and
 * refresh.
 */
public final class IntegrationRegistry {

    private static final IntegrationRegistry INSTANCE = new IntegrationRegistry();

    private final CopyOnWriteArrayList<ModIntegrationAdapter> adapters = new CopyOnWriteArrayList<>();
    private final AtomicBoolean bootstrapped = new AtomicBoolean(false);

    private IntegrationRegistry() {
        registerBuiltIns();
    }

    public static IntegrationRegistry getInstance() {
        return INSTANCE;
    }

    public static void bootstrapClient() {
        getInstance().bootstrap(IntegrationContext.client());
    }

    public void register(ModIntegrationAdapter adapter) {
        if (adapter == null) return;
        for (ModIntegrationAdapter existing : adapters) {
            if (existing.id().equalsIgnoreCase(adapter.id())) return;
        }
        adapters.add(adapter);
    }

    public List<ModIntegrationAdapter> adapters() {
        return Collections.unmodifiableList(adapters);
    }

    public List<ModIntegrationAdapter> activeAdapters() {
        List<ModIntegrationAdapter> active = new ArrayList<>();
        for (ModIntegrationAdapter adapter : adapters) {
            if (safeIsLoaded(adapter)) active.add(adapter);
        }
        return active;
    }

    public boolean isLoaded(String modIdOrAdapterId) {
        if (modIdOrAdapterId == null || modIdOrAdapterId.isBlank()) return false;
        String normalized = modIdOrAdapterId.toLowerCase(Locale.ROOT);
        for (ModIntegrationAdapter adapter : adapters) {
            if (adapter.id().equalsIgnoreCase(normalized) && safeIsLoaded(adapter)) return true;
            for (String modId : adapter.modIds()) {
                if (modId != null && modId.equalsIgnoreCase(normalized) && safeIsLoaded(adapter)) return true;
            }
        }
        return ProjectBabelCommon.platform().mods().isLoaded(normalized);
    }

    public boolean isAnyLoaded(String... modIds) {
        if (modIds == null || modIds.length == 0) return true;
        for (String modId : modIds) {
            if (isLoaded(modId)) return true;
        }
        return false;
    }

    public void bootstrap(IntegrationContext context) {
        Objects.requireNonNull(context, "context");
        if (!bootstrapped.compareAndSet(false, true)) return;

        for (ModIntegrationAdapter adapter : adapters) {
            if (!safeIsLoaded(adapter)) continue;
            try {
                adapter.bootstrap(context);
                ProjectBabelCommon.LOGGER.debug(
                    "[projectbabel] Integracao ativa: {} ({})",
                    adapter.displayName(),
                    String.join(",", adapter.modIds())
                );
            } catch (Throwable e) {
                ProjectBabelCommon.LOGGER.warn(
                    "[projectbabel] Falha ao iniciar integracao {}: {}",
                    adapter.displayName(),
                    e.getMessage()
                );
            }
        }
    }

    public void resetRuntimeStateAll() {
        forEachActive("reset", ModIntegrationAdapter::resetRuntimeState);
    }

    public void requestWorldPreloadAll() {
        forEachActive("world preload", ModIntegrationAdapter::requestWorldPreload);
    }

    public void refreshOpenUiAll(String reason) {
        forEachActive("ui refresh", adapter -> adapter.refreshOpenUi(reason));
    }

    public void preloadForScreen(String integrationId, Object screen, PreloadMode mode) {
        if (integrationId == null || integrationId.isBlank()) return;
        PreloadMode actualMode = mode == null ? PreloadMode.ASYNC : mode;
        String normalized = integrationId.toLowerCase(Locale.ROOT);
        forEachActive("screen preload", adapter -> {
            if (adapter.id().equalsIgnoreCase(normalized)
                || adapter.modIds().stream().anyMatch(modId -> modId.equalsIgnoreCase(normalized))) {
                adapter.preloadForScreen(screen, actualMode);
            }
        });
    }

    public void onCacheInvalidated() {
        resetRuntimeStateAll();
        requestWorldPreloadAll();
        refreshOpenUiAll("cache limpo/alterado");
    }

    private void registerBuiltIns() {
        register(new FTBQuestsAdapter());
        register(new PatchouliAdapter());
        register(new ModonomiconAdapter());
        register(new GuideMeAdapter());
        register(new CreatePonderAdapter());
    }


    private boolean safeIsLoaded(ModIntegrationAdapter adapter) {
        try {
            return adapter.isLoaded();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void forEachActive(String action, IntegrationAction actionCallback) {
        for (ModIntegrationAdapter adapter : adapters) {
            if (!safeIsLoaded(adapter)) continue;
            try {
                actionCallback.run(adapter);
            } catch (Throwable e) {
                ProjectBabelCommon.LOGGER.debug(
                    "[projectbabel] Integracao {} ignorou {}: {}",
                    adapter.displayName(),
                    action,
                    e.getMessage()
                );
            }
        }
    }

    @FunctionalInterface
    private interface IntegrationAction {
        void run(ModIntegrationAdapter adapter);
    }
}
