package com.projectbabel;

import com.projectbabel.config.AutoTranslateConfig;
import com.projectbabel.translation.LanguageDetector;
import com.projectbabel.translation.TranslationManager;
import com.projectbabel.translation.UniversalTermsDictionary;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(ProjectBabelMod.MOD_ID)
public class ProjectBabelMod {

    public static final String MOD_ID = "projectbabel";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    public ProjectBabelMod() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        ModLoadingContext.get().registerConfig(
            ModConfig.Type.CLIENT, AutoTranslateConfig.SPEC, "projectbabel-client.toml");

        modEventBus.addListener(this::clientSetup);
    }

    private void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            TranslationManager.getInstance();
            UniversalTermsDictionary.getInstance().ensureLoadedAsync();
            LOGGER.info("[projectbabel] Pronto. Engines=Google/Lingva {}->{}",
                AutoTranslateConfig.getSourceLang(),
                LanguageDetector.getTargetLanguageForApi());
        });
    }
}
