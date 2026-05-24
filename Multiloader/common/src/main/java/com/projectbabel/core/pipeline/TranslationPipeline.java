package com.projectbabel.core.pipeline;

import com.projectbabel.api.TranslationContext;
import com.projectbabel.api.TranslationSurface;
import com.projectbabel.ProjectBabelCommon;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.LiteralContents;
import net.minecraft.network.chat.contents.TranslatableContents;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;

import com.projectbabel.minecraft.tooltip.EnchantmentDescriptionTranslator;
import com.projectbabel.core.text.LanguageDetector;
import com.projectbabel.core.schedule.PreloadAcceleration;
import com.projectbabel.core.text.TextFilter;
import com.projectbabel.core.text.TextFormatUtils;
import com.projectbabel.core.dictionary.TranslationDictionary;
import com.projectbabel.core.schedule.TranslationExecutors;
import com.projectbabel.core.schedule.TranslationPriority;
import com.projectbabel.core.service.TranslationManager;
import com.projectbabel.core.dictionary.UniversalTermsDictionary;
/**
 * Fast triage path for text intercepted by mixins.
 *
 * Order is strict:
 * 1. Native Minecraft/mod/resource-pack translation key.
 * 2. Context cache keyed by target language + namespace + translation key.
 * 3. Lightweight language detection before any external translation request.
 */
public final class TranslationPipeline {

    private static final String LITERAL_NAMESPACE = "literal";

    private TranslationPipeline() {}

    /**
     * Context-aware entrypoint. New call sites should use this overload so the
     * pipeline can distinguish render fallback, UI tick, chat, tooltip, books,
     * quests and preload behavior.
     */
    public static Component translateComponent(Component original, TranslationContext context) {
        if (original == null) return null;
        TranslationContext resolved = normalizeContext(context);
        if (resolved.cacheOnly() || !resolved.allowScheduling()) {
            return translateComponentTreeCacheOnly(original);
        }
        if (resolved.allowBlocking()) {
            return translateComponentTreeBlocking(original);
        }
        return translateComponentTree(original);
    }

    public static String translateLanguageLookup(String key, String resolved, TranslationContext context) {
        if (resolved == null || resolved.isEmpty()) return resolved;
        TranslationContext normalized = normalizeContext(context);
        if (normalized.cacheOnly() || !normalized.allowScheduling()) {
            return translateStringCacheOnly(resolved);
        }
        return TranslationTriageManager.getInstance().triageLanguageLookup(key, resolved);
    }

    public static String translateString(String text, TranslationContext context) {
        if (text == null || text.isEmpty()) return text;
        TranslationContext resolved = normalizeContext(context);
        if (resolved.cacheOnly() || !resolved.allowScheduling()) {
            return translateStringCacheOnly(text);
        }
        if (resolved.allowBlocking()) {
            return translateStringBlocking(text);
        }
        return translateString(text);
    }

    private static TranslationContext normalizeContext(TranslationContext context) {
        return context == null
            ? TranslationContext.interactive(TranslationSurface.UNKNOWN)
            : context;
    }

    public static Component translateComponent(Component original) {
        return translateComponentTree(original);
    }

    public static Component translateComponentTree(Component original) {
        Component translated = TranslationTriageManager.getInstance().triageComponentTree(original);
        if (isMeaningfulReplacement(original, translated)) {
            TranslationSkipRegistry.skip(translated);
        }
        return translated;
    }

    public static Component translateComponentTreeCacheOnly(Component original) {
        Component translated = TranslationTriageManager.getInstance().triageComponentTreeCacheOnly(original);
        if (isMeaningfulReplacement(original, translated)) {
            TranslationSkipRegistry.skip(translated);
        }
        return translated;
    }

    public static CompletableFuture<Component> translateComponentTreeBlockingAsync(Component original) {
        return CompletableFuture.supplyAsync(
            () -> translateComponentTreeBlocking(original),
            PreloadAcceleration.isActive() ? TranslationExecutors.preload() : TranslationExecutors.triage(TranslationPriority.NORMAL)
        );
    }

    public static Component translateComponentTreeBlocking(Component original) {
        if (original == null || !ProjectBabelCommon.config().isEnabled()) return original;
        if (!LanguageDetector.shouldModBeActive()) return original;
        if (TranslationSkipRegistry.shouldSkipIdentity(original)) return original;

        if (original.getContents() instanceof TranslatableContents translatable) {
            return translateTranslatableTreeBlocking(original, translatable);
        }

        if (original.getContents() instanceof LiteralContents literal) {
            String originalLiteral = literal.text();
            String translatedLiteral = translateStringBlocking(originalLiteral);

            boolean changed = translatedLiteral != null && !translatedLiteral.equals(originalLiteral);
            MutableComponent rebuilt = changed
                ? Component.literal(translatedLiteral).withStyle(original.getStyle() == null ? Style.EMPTY : original.getStyle())
                : null;

            for (Component sibling : original.getSiblings()) {
                Component translatedSibling = translateComponentTreeBlocking(sibling);
                if (translatedSibling != sibling && rebuilt == null) {
                    rebuilt = Component.literal(originalLiteral)
                        .withStyle(original.getStyle() == null ? Style.EMPTY : original.getStyle());
                }
                if (rebuilt != null) rebuilt.append(translatedSibling);
            }

            return rebuilt == null ? original : rebuilt;
        }

        String fullText = original.getString();
        if (original.getSiblings().isEmpty()) {
            String translated = translateStringBlocking(fullText);
            return translated.equals(fullText) ? original : literalLike(original, translated);
        }

        // Unknown component contents with siblings should not be flattened into one
        // literal, otherwise hover/click/style/lore subtrees can disappear.
        MutableComponent rebuilt = null;
        for (Component sibling : original.getSiblings()) {
            Component translatedSibling = translateComponentTreeBlocking(sibling);
            if (translatedSibling != sibling && rebuilt == null) {
                rebuilt = Component.empty().withStyle(original.getStyle() == null ? Style.EMPTY : original.getStyle());
            }
            if (rebuilt != null) rebuilt.append(translatedSibling);
        }
        return rebuilt == null ? original : rebuilt;
    }

    public static Component translateEnchantmentDescription(Component original) {
        return EnchantmentDescriptionTranslator.translate(original);
    }

    public static boolean isEnchantmentDescriptionComponent(Component component) {
        return EnchantmentDescriptionTranslator.isDescriptionComponent(component);
    }

    public static boolean needsExternalTranslation(Component original) {
        if (TranslationSkipRegistry.shouldSkip(original)) return false;
        if (original == null || !ProjectBabelCommon.config().isEnabled()) return false;
        if (!LanguageDetector.shouldModBeActive()) return false;

        if (original.getContents() instanceof TranslatableContents translatable) {
            if (!hasNativeTranslation(translatable.getKey())
                && needsExternalText(original.getString(), extractNamespace(translatable.getKey()), translatable.getKey())) {
                return true;
            }

            Object[] args = translatable.getArgs();
            for (int i = 0; i < args.length; i++) {
                if (isChatSenderArgument(translatable.getKey(), i)) continue;
                if (needsExternalArgument(args[i])) return true;
            }
        } else if (original.getContents() instanceof LiteralContents literal) {
            if (needsExternalText(literal.text(), LITERAL_NAMESPACE, literal.text())) return true;
        } else if (needsExternalText(original.getString(), LITERAL_NAMESPACE, original.getString())) {
            return true;
        }

        for (Component sibling : original.getSiblings()) {
            if (needsExternalTranslation(sibling)) return true;
        }
        return false;
    }

    private static boolean needsExternalArgument(Object arg) {
        if (arg instanceof Component component) {
            return needsExternalTranslation(component);
        }
        if (arg instanceof String string) {
            return needsExternalText(string, LITERAL_NAMESPACE, string);
        }
        return false;
    }

    private static boolean isChatSenderArgument(String key, int index) {
        return index == 0 && key != null && key.startsWith("chat.type.");
    }

    private static Component translateTranslatableTreeBlocking(Component original, TranslatableContents translatable) {
        Object[] args = translatable.getArgs();
        Object[] translatedArgs = args;
        boolean changed = false;
        String key = translatable.getKey();

        for (int i = 0; i < args.length; i++) {
            if (isChatSenderArgument(key, i)) continue;

            Object arg = args[i];
            Object translatedArg = translateArgumentBlocking(arg);
            if (translatedArg != arg) {
                if (translatedArgs == args) translatedArgs = args.clone();
                translatedArgs[i] = translatedArg;
                changed = true;
            }
        }

        MutableComponent base = Component.translatable(key, translatedArgs)
            .withStyle(original.getStyle() == null ? Style.EMPTY : original.getStyle());

        boolean nativeTranslation = hasNativeTranslation(key);
        boolean baseChanged = false;
        MutableComponent translatedBase = base;

        if (!nativeTranslation) {
            String baseText = base.getString();
            String translated = translateStringBlocking(baseText);
            if (!translated.equals(baseText)) {
                translatedBase = literalLike(base, translated);
                baseChanged = true;
            }
        }

        if (!changed && !baseChanged && original.getSiblings().isEmpty()) {
            return nativeTranslation ? original : base;
        }

        MutableComponent rebuilt = translatedBase;
        for (Component sibling : original.getSiblings()) {
            rebuilt.append(translateComponentTreeBlocking(sibling));
        }
        return rebuilt;
    }

    private static Object translateArgumentBlocking(Object arg) {
        if (arg instanceof Component component) {
            return translateComponentTreeBlocking(component);
        }
        if (arg instanceof String string) {
            String translated = translateStringBlocking(string);
            return translated.equals(string) ? arg : translated;
        }
        return arg;
    }

    public static String translateString(String text) {
        String translated = TranslationTriageManager.getInstance().triageString(text);
        if (translated != null && text != null && !translated.equals(text)) {
            TranslationSkipRegistry.skipText(translated);
        }
        return translated;
    }

    public static String translateStringBlocking(String text) {
        if (text == null || text.isEmpty()) return text;
        if (!ProjectBabelCommon.config().isEnabled()) return text;
        if (!LanguageDetector.shouldModBeActive()) return text;

        String plain = TextFormatUtils.stripFormatting(text).trim();
        if (!TextFilter.shouldTranslate(plain)) return text;

        String targetLang = LanguageDetector.getTargetLanguageForApi();
        String universal = UniversalTermsDictionary.getInstance().lookupExact(plain, ProjectBabelCommon.config().getSourceLang(), targetLang);
        if (universal != null && !universal.isBlank()) {
            return TextFormatUtils.preserveEdgeWhitespace(text, universal);
        }

        String dictionary = TranslationDictionary.getInstance().lookupExact(plain);
        if (dictionary != null && !dictionary.isBlank()) {
            return TextFormatUtils.preserveEdgeWhitespace(text, dictionary);
        }

        TranslationManager manager = TranslationManager.getInstance();
        String cached = manager.getCachedTranslation(text, ProjectBabelCommon.config().getSourceLang(), targetLang);
        if (cached == null || cached.isBlank()) {
            cached = manager.getCachedTranslationAnySource(text, targetLang);
        }
        if (cached != null && !cached.isBlank()) {
            return TextFormatUtils.preserveEdgeWhitespace(text, cached);
        }

        if (TranslationSkipRegistry.shouldSkip(text)) return text;

        String detectedLanguage = LanguageDetector.detectLanguage(plain);
        if (targetLang.equals(detectedLanguage)) return text;

        String sourceLang = detectedLanguage.equals("unknown")
            ? ProjectBabelCommon.config().getSourceLang()
            : detectedLanguage;

        cached = manager.getCachedTranslation(text, sourceLang, targetLang);
        if (cached == null || cached.isBlank()) {
            cached = manager.getCachedTranslationAnySource(text, targetLang);
        }
        if (cached != null && !cached.isBlank()) {
            return TextFormatUtils.preserveEdgeWhitespace(text, cached);
        }

        return manager.getTranslationPreservingFormatBlocking(text, sourceLang, targetLang);
    }

    public static String translateStringCacheOnly(String text) {
        return TranslationTriageManager.getInstance().triageStringCacheOnly(text);
    }

    public static Component collapseTooltipRomanDuplicate(Component component) {
        if (component == null) return null;

        // Do not flatten complex tooltip components. Enchanted books and modded
        // lore lines often carry their visible text as siblings; replacing the
        // whole component with one literal removes that nested description.
        if (!component.getSiblings().isEmpty()) return component;

        String text = component.getString();
        String collapsed = TextFormatUtils.collapseDuplicateTrailingRoman(text);
        if (collapsed == null || collapsed.equals(text)) return component;
        Style style = component.getStyle();
        Component result = Component.literal(collapsed).withStyle(style == null ? Style.EMPTY : style);
        TranslationSkipRegistry.skip(result);
        return result;
    }

    private static boolean needsExternalText(String text, String namespace, String translationKey) {
        if (text == null || text.isBlank()) return false;

        String plain = TextFormatUtils.stripFormatting(text).trim();
        if (!TextFilter.shouldTranslate(plain)) return false;

        String targetLang = LanguageDetector.getTargetLanguageForApi();
        String cacheKey = buildContextKey(LanguageDetector.getClientLanguageCode(), namespace, translationKey);
        if (TranslationTriageManager.getInstance().hasCachedText(cacheKey)) return false;

        TranslationManager manager = TranslationManager.getInstance();
        if (manager.isAlreadyTranslatedValue(text)) return false;
        if (manager.getCachedTranslation(text, ProjectBabelCommon.config().getSourceLang(), targetLang) != null) return false;

        String detectedLanguage = LanguageDetector.detectLanguage(plain);
        return !targetLang.equals(detectedLanguage);
    }

    private static boolean hasNativeTranslation(String key) {
        if (key == null || key.isBlank()) return false;
        if (!I18n.exists(key)) return false;

        String translated = I18n.get(key);
        if (translated == null || translated.isBlank() || translated.equals(key)) return false;

        String targetLang = LanguageDetector.getTargetLanguageForApi();
        if ("en".equals(targetLang)) return true;

        if (!TextFilter.shouldTranslate(translated)) return true;

        String plain = TextFormatUtils.stripFormatting(translated).trim();
        if (!TextFilter.shouldTranslate(plain)) return true;

        String detected = LanguageDetector.detectLanguage(plain);
        return targetLang.equals(detected);
    }

    private static MutableComponent literalLike(Component original, String translated) {
        Style style = original.getStyle();
        return Component.literal(translated).withStyle(style == null ? Style.EMPTY : style);
    }

    private static boolean isMeaningfulReplacement(Component original, Component translated) {
        if (translated == null || translated == original) return false;
        if (original == null) return true;

        String before = original.getString();
        String after = translated.getString();
        return before == null || after == null || !after.equals(before);
    }

    public static String buildContextKey(String targetLang, String namespace, String translationKey) {
        return targetLang + ':' + namespace + ':' + translationKey;
    }

    public static void clearContextCache() {
        TranslationTriageManager.getInstance().clearContextCache();
        EnchantmentDescriptionTranslator.clear();
    }

    private static String extractNamespace(String key) {
        int colon = key.indexOf(':');
        if (colon > 0) return key.substring(0, colon);

        int firstDot = key.indexOf('.');
        if (firstDot <= 0) return LITERAL_NAMESPACE;

        String firstPart = key.substring(0, firstDot);
        int secondDot = key.indexOf('.', firstDot + 1);

        if (hasNamespaceAfterPrefix(firstPart) && secondDot > firstDot + 1) {
            return key.substring(firstDot + 1, secondDot);
        }

        if (isVanillaPrefix(firstPart)) return "minecraft";
        return firstPart.toLowerCase(Locale.ROOT);
    }

    private static boolean hasNamespaceAfterPrefix(String prefix) {
        return switch (prefix) {
            case "item", "block", "entity", "fluid", "effect", "enchantment",
                 "biome", "attribute", "stat", "subtitles" -> true;
            default -> false;
        };
    }

    private static boolean isVanillaPrefix(String prefix) {
        return switch (prefix) {
            case "gui", "menu", "options", "controls", "key", "chat",
                 "multiplayer", "selectWorld", "resourcePack", "commands" -> true;
            default -> false;
        };
    }
}
