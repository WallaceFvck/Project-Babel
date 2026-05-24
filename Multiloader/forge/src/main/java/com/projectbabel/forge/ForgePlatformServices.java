package com.projectbabel.forge;

import com.projectbabel.platform.BabelConfigView;
import com.projectbabel.platform.ClientExecutor;
import com.projectbabel.platform.ModLookup;
import com.projectbabel.platform.PathsProvider;
import com.projectbabel.platform.PlatformServices;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.file.Path;

/** Forge implementation of the loader services required by common code. */
public final class ForgePlatformServices implements PlatformServices {
    private final BabelConfigView config = new ForgeConfigBridge();
    private final ModLookup mods = modId -> ModList.get().isLoaded(modId);
    private final PathsProvider paths = new PathsProvider() {
        @Override public Path gameDir() { return FMLPaths.GAMEDIR.get(); }
        @Override public Path configDir() { return FMLPaths.CONFIGDIR.get(); }
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
