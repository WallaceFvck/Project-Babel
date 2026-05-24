package com.projectbabel.fabric;

import com.projectbabel.platform.BabelConfigView;
import com.projectbabel.platform.ClientExecutor;
import com.projectbabel.platform.ModLookup;
import com.projectbabel.platform.PathsProvider;
import com.projectbabel.platform.PlatformServices;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

import java.nio.file.Path;

/** Fabric implementation of the loader services required by common code. */
public final class FabricPlatformServices implements PlatformServices {
    private final BabelConfigView config = new FabricConfigBridge();
    private final ModLookup mods = modId -> FabricLoader.getInstance().isModLoaded(modId);
    private final PathsProvider paths = new PathsProvider() {
        @Override public Path gameDir() { return FabricLoader.getInstance().getGameDir(); }
        @Override public Path configDir() { return FabricLoader.getInstance().getConfigDir(); }
    };
    private final ClientExecutor clientExecutor = new ClientExecutor() {
        @Override public boolean isClientThread() {
            Minecraft client = Minecraft.getInstance();
            return client != null && client.isSameThread();
        }

        @Override public void execute(Runnable task) {
            Minecraft client = Minecraft.getInstance();
            if (client == null) return;
            client.execute(task);
        }
    };

    @Override public BabelConfigView config() { return config; }
    @Override public ModLookup mods() { return mods; }
    @Override public PathsProvider paths() { return paths; }
    @Override public ClientExecutor clientExecutor() { return clientExecutor; }
}
