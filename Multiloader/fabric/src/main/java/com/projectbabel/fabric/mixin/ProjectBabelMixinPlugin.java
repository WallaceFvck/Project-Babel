package com.projectbabel.fabric.mixin;

import net.fabricmc.loader.api.FabricLoader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Optional compat gate for Project Babel mixins.
 *
 * Vanilla mixins are always allowed. Compat mixins are allowed only when the
 * matching target mod is loaded. This prevents optional integrations from
 * becoming hard runtime dependencies when a modpack does not include a target
 * mod or when a target mod was temporarily removed while debugging.
 *
 * Add this class to the mixin json with:
 *   "plugin": "com.projectbabel.fabric.mixin.ProjectBabelMixinPlugin"
 */
public final class ProjectBabelMixinPlugin implements IMixinConfigPlugin {

    private static final Logger LOGGER = LogManager.getLogger("projectbabel-mixin");
    private static final Set<String> LOGGED_DECISIONS = Collections.synchronizedSet(new HashSet<>());
    private static final List<CompatGate> COMPAT_GATES = new ArrayList<>();

    static {
        COMPAT_GATES.add(new CompatGate(".mixin.compat.ae2.", "Applied Energistics 2", "ae2"));
        COMPAT_GATES.add(new CompatGate(".mixin.compat.create.", "Create", "create"));
        COMPAT_GATES.add(new CompatGate(".mixin.compat.enchdesc.", "Enchantment Descriptions", "enchdesc", "enchantmentdescriptions"));
        COMPAT_GATES.add(new CompatGate(".mixin.compat.ftblibrary.", "FTB Library", "ftblibrary"));
        COMPAT_GATES.add(new CompatGate(".mixin.compat.ftbquests.", "FTB Quests", "ftbquests"));
        COMPAT_GATES.add(new CompatGate(".mixin.compat.guideme.", "GuideME", "guideme", "guide_me"));
        COMPAT_GATES.add(new CompatGate(".mixin.compat.jade.", "Jade", "jade"));
        COMPAT_GATES.add(new CompatGate(".mixin.compat.modonomicon.", "Modonomicon", "modonomicon"));
        COMPAT_GATES.add(new CompatGate(".mixin.compat.patchouli.", "Patchouli", "patchouli"));
        COMPAT_GATES.add(new CompatGate(".mixin.compat.refinedstorage.", "Refined Storage", "refinedstorage"));
    }

    @Override
    public void onLoad(String mixinPackage) {
        logOnce("load:" + mixinPackage, "Project Babel mixin gate loaded for package {}", mixinPackage);
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        CompatGate gate = gateFor(mixinClassName);
        if (gate == null) {
            return true;
        }

        boolean loaded = gate.isLoaded();
        String key = (loaded ? "apply:" : "skip:") + gate.name;
        if (loaded) {
            logOnce(key, "Applying Project Babel compat mixins for {}.", gate.name);
        } else {
            logOnce(key, "Skipping Project Babel compat mixins for {} because none of these mod ids are loaded: {}", gate.name, String.join(", ", gate.modIds));
        }
        return loaded;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
        // No target mutation needed.
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        // No bytecode pre-processing needed.
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        // No bytecode post-processing needed.
    }

    private static CompatGate gateFor(String mixinClassName) {
        if (mixinClassName == null) return null;
        String normalized = mixinClassName.toLowerCase(Locale.ROOT);
        for (CompatGate gate : COMPAT_GATES) {
            if (normalized.contains(gate.packageMarker)) {
                return gate;
            }
        }
        return null;
    }

    private static void logOnce(String key, String message, Object... args) {
        if (LOGGED_DECISIONS.add(key)) {
            LOGGER.info(message, args);
        }
    }

    private static final class CompatGate {
        final String packageMarker;
        final String name;
        final String[] modIds;

        CompatGate(String packageMarker, String name, String... modIds) {
            this.packageMarker = packageMarker.toLowerCase(Locale.ROOT);
            this.name = name;
            this.modIds = modIds;
        }

        boolean isLoaded() {
            FabricLoader loader = FabricLoader.getInstance();
            for (String modId : modIds) {
                if (loader.isModLoaded(modId)) return true;
            }
            return false;
        }
    }
}
