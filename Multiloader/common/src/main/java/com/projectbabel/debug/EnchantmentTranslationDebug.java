package com.projectbabel.debug;

import com.projectbabel.ProjectBabelCommon;
import com.projectbabel.core.text.LanguageDetector;
import com.projectbabel.core.service.TranslationManager;
import com.projectbabel.core.pipeline.TranslationSkipRegistry;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class EnchantmentTranslationDebug {

    private static final int MAX_LOGS_PER_WORLD = 220;
    private static final Set<String> LOGGED = ConcurrentHashMap.newKeySet();
    private static final AtomicInteger WORLD = new AtomicInteger(0);
    private static final AtomicInteger LOG_COUNT = new AtomicInteger(0);

    private EnchantmentTranslationDebug() {
    }

    public static void onWorldJoin() {
        if (!enabled()) return;

        LOGGED.clear();
        LOG_COUNT.set(0);
        int world = WORLD.incrementAndGet();
        TranslationManager manager = TranslationManager.getInstance();
        ProjectBabelCommon.LOGGER.info(
            "[projectbabel][ench-debug] worldJoin#{} target={} source={} cache={} pending={} queue={} engine={} turbo={}",
            world,
            LanguageDetector.getTargetLanguageForApi(),
            ProjectBabelCommon.config().getSourceLang(),
            manager.getCache().size(),
            manager.getPendingCount(),
            manager.getQueuedCount(),
            manager.getActiveEngineName(),
            ProjectBabelCommon.config().isTurboMode()
        );
    }

    public static void tooltipLine(int index, TooltipFlag flag, Component before, Component after) {
        if (!enabled() || before == null || !looksRelevant(before)) return;

        String text = before.getString();
        String key = "tooltip:" + WORLD.get() + ':' + index + ':' + text;
        logOnce(key,
            "tooltip line={} advanced={} changed={} before={} after={} probeBefore={} probeAfter={}",
            index,
            flag != null && flag.isAdvanced(),
            after != null && after != before,
            describe(before),
            describe(after),
            probe(before),
            probe(after)
        );
    }

    public static void enchantmentDescription(Enchantment enchantment, Component before, Component after) {
        if (!enabled() || before == null) return;

        ResourceLocation id = null;
        try {
            id = BuiltInRegistries.ENCHANTMENT.getKey(enchantment);
        } catch (Exception ignored) {
        }

        String key = "desc:" + WORLD.get() + ':' + id + ':' + before.getString();
        logOnce(key,
            "description enchant={} changed={} before={} after={} probeBefore={} probeAfter={}",
            id,
            after != null && after != before,
            describe(before),
            describe(after),
            probe(before),
            probe(after)
        );
    }

    private static boolean enabled() {
        try {
            return ProjectBabelCommon.config().isDebugEnchantments();
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean looksRelevant(Component component) {
        if (containsEnchantmentKey(component)) return true;

        String text = component.getString();
        if (text == null || text.isBlank()) return false;

        return text.matches(".*\\b[IVXLCDM]{1,8}\\b.*")
            || text.toLowerCase(java.util.Locale.ROOT).contains("enchant");
    }

    private static boolean containsEnchantmentKey(Component component) {
        if (component == null) return false;

        if (component.getContents() instanceof TranslatableContents translatable) {
            String key = translatable.getKey();
            if (key != null && key.contains("enchantment")) return true;
        }

        for (Component sibling : component.getSiblings()) {
            if (containsEnchantmentKey(sibling)) return true;
        }

        if (component.getContents() instanceof TranslatableContents translatable) {
            for (Object arg : translatable.getArgs()) {
                if (arg instanceof Component argComponent && containsEnchantmentKey(argComponent)) {
                    return true;
                }
            }
        }

        return false;
    }

    private static String describe(Component component) {
        if (component == null) return "null";

        String key = "-";
        int args = 0;
        if (component.getContents() instanceof TranslatableContents translatable) {
            key = translatable.getKey();
            args = translatable.getArgs().length;
        }

        return "{type=" + component.getContents().getClass().getSimpleName()
            + ",key=" + key
            + ",args=" + args
            + ",siblings=" + component.getSiblings().size()
            + ",skipIdentity=" + TranslationSkipRegistry.shouldSkipIdentity(component)
            + ",skipText=" + TranslationSkipRegistry.shouldSkip(component.getString())
            + ",skipReason=" + TranslationSkipRegistry.reasonFor(component)
            + ",text=\"" + truncate(component.getString(), 96) + "\"}";
    }

    private static String probe(Component component) {
        if (component == null) return "null";

        String text = component.getString();
        String target = LanguageDetector.getTargetLanguageForApi();
        TranslationManager manager = TranslationManager.getInstance();

        String exact = manager.getCachedTranslation(text, ProjectBabelCommon.config().getSourceLang(), target);
        String any = exact == null ? manager.getCachedTranslationAnySource(text, target) : exact;
        boolean alreadyTranslated = manager.isAlreadyTranslatedValue(text);

        String base = null;
        String baseAny = null;
        if (component.getContents() instanceof TranslatableContents translatable) {
            base = resolveKey(translatable.getKey());
            baseAny = manager.getCachedTranslation(base, ProjectBabelCommon.config().getSourceLang(), target);
            if (baseAny == null) {
                baseAny = manager.getCachedTranslationAnySource(base, target);
            }
        }

        return "{cacheExact=" + (exact != null)
            + ",cacheAny=" + (any != null)
            + ",alreadyTranslated=" + alreadyTranslated
            + ",base=\"" + truncate(base, 64) + "\""
            + ",baseCache=" + (baseAny != null)
            + '}';
    }

    private static String resolveKey(String key) {
        if (key == null || key.isBlank()) return null;
        try {
            String resolved = I18n.get(key);
            return resolved == null || resolved.isBlank() ? key : resolved;
        } catch (Exception ignored) {
            return key;
        }
    }

    private static void logOnce(String key, String message, Object... args) {
        if (LOG_COUNT.get() >= MAX_LOGS_PER_WORLD) return;
        if (!LOGGED.add(key)) return;
        if (LOG_COUNT.incrementAndGet() > MAX_LOGS_PER_WORLD) return;
        ProjectBabelCommon.LOGGER.info("[projectbabel][ench-debug] " + message, args);
    }

    private static String truncate(String text, int max) {
        if (text == null) return null;
        if (text.length() <= max) return text;
        return text.substring(0, Math.max(0, max - 3)) + "...";
    }
}
