package com.projectbabel.forge;

import com.projectbabel.ProjectBabelCommon;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(ProjectBabelCommon.MOD_ID)
public class ProjectBabelForge {


    public ProjectBabelForge() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        ModLoadingContext.get().registerConfig(
            ModConfig.Type.CLIENT, ForgeConfigBridge.spec(), "projectbabel-client.toml");

        ProjectBabelCommon.init(new ForgePlatformServices());

        modEventBus.addListener(this::clientSetup);
    }

    private void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(ForgeClientBootstrap::init);
    }
}
