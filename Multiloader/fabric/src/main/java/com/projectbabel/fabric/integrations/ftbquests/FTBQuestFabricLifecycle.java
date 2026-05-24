package com.projectbabel.fabric.integrations.ftbquests;

import com.projectbabel.integrations.ftbquests.FTBQuestAutoTranslator;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;

import java.util.concurrent.atomic.AtomicBoolean;

/** Fabric lifecycle glue for the rewritten FTB Quests integration. */
public final class FTBQuestFabricLifecycle {

    private static final AtomicBoolean REGISTERED = new AtomicBoolean(false);
    private static final AtomicBoolean IGNORE_REFRESH_CALLBACK = new AtomicBoolean(false);

    private FTBQuestFabricLifecycle() {}

    public static void register() {
        if (!REGISTERED.compareAndSet(false, true)) return;
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> onClientJoinWorld());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
    }

    public static void onClientJoinWorld() {
        if (!isFtbQuestsLoaded()) return;
        reset();
        FTBQuestAutoTranslator.preloadCurrentClientFile();
    }

    public static void onClientQuestFileSynced() {
        if (!isFtbQuestsLoaded()) return;
        FTBQuestAutoTranslator.onQuestFileSynced(null);
    }

    public static void onFtbRefreshGui(Object clientQuestFile) {
        if (IGNORE_REFRESH_CALLBACK.get()) return;
        if (isFtbQuestsLoaded()) FTBQuestAutoTranslator.preloadFile(clientQuestFile);
    }

    static void runIgnoringRefreshCallback(Runnable action) {
        if (action == null) return;
        boolean previous = IGNORE_REFRESH_CALLBACK.get();
        IGNORE_REFRESH_CALLBACK.set(true);
        try {
            action.run();
        } finally {
            IGNORE_REFRESH_CALLBACK.set(previous);
        }
    }

    public static void reset() {
        FTBQuestAutoTranslator.reset();
    }

    public static boolean isFtbQuestsLoaded() {
        return FabricLoader.getInstance().isModLoaded("ftbquests");
    }
}
