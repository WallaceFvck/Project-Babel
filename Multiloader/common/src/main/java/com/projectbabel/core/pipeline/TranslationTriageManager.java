package com.projectbabel.core.pipeline;

import com.projectbabel.ProjectBabelCommon;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.LiteralContents;
import net.minecraft.network.chat.contents.TranslatableContents;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;

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
 * Hot-path triage for render-facing translation hooks.
 *
 * Render thread contract:
 * - native translation keys return immediately;
 * - cache hits return immediately;
 * - misses only enqueue work and return the original value;
 * - language detection and API calls always happen on the triage executor.
 */
public final class TranslationTriageManager {

    private static final TranslationTriageManager INSTANCE = new TranslationTriageManager();

    private static final String LITERAL_NAMESPACE = "literal";
    private static final int MAX_CONTEXT_CACHE_SIZE = 250_000;
    private static final int MAX_COMPONENT_CACHE_SIZE = 64_000;

    private final ConcurrentHashMap<String, String> textCache = new ConcurrentHashMap<>(32768);
    private final ConcurrentHashMap<String, Component> componentCache = new ConcurrentHashMap<>(8192);
    private final ConcurrentHashMap<String, Boolean> nativeTranslationCache = new ConcurrentHashMap<>(8192);
    private final ConcurrentHashMap<String, Boolean> targetLanguageTextCache = new ConcurrentHashMap<>(8192);
    private final ConcurrentHashMap<String, Boolean> pending = new ConcurrentHashMap<>(32768);
    private final AtomicLong generation = new AtomicLong(0L);

    private volatile String lastClientLanguage = "";
    private volatile String lastConfiguredTarget = "";
    private volatile boolean lastFollowClientLanguage = true;
    private volatile String lastCacheLanguage = "en";
    private volatile String lastApiLanguage = "en";

    private TranslationTriageManager() {}

    public static TranslationTriageManager getInstance() {
        return INSTANCE;
    }

    public Component triageComponentTree(Component original) {
        if (original == null) return null;
        if (!canRun()) return original;
        if (TranslationSkipRegistry.shouldSkipIdentity(original)) return original;

        if (original.getContents() instanceof TranslatableContents translatable) {
            String key = translatable.getKey();
            if (key == null || key.isEmpty()) return original;

            String contextKey = buildContextKey(cacheLanguage(), extractNamespace(key), key);
            String componentKey = componentContextKey(contextKey, original);

            Component cached = sanitizeCachedComponent(original.getString(), componentKey, componentCache.get(componentKey));
            if (cached != null) return cached;

            String cachedText = findCachedTranslatableText(original, translatable, contextKey);
            boolean cachedTextChanged = isChangedText(original.getString(), cachedText);
            if (cachedTextChanged && shouldPreferExplicitCache(key)) {
                if (canReplaceWithLiteral(original, translatable)) {
                    return literalLike(original, cachedText);
                }
                if (canReplaceBaseWithLiteral(original, translatable)) {
                    return literalBaseWithSiblings(original, cachedText, false);
                }
            }

            if (hasValidNativeTranslation(contextKey, key)) {
                return original;
            }

            if (cachedTextChanged) {
                if (canReplaceWithLiteral(original, translatable)) {
                    return literalLike(original, cachedText);
                }
                if (canReplaceBaseWithLiteral(original, translatable)) {
                    return literalBaseWithSiblings(original, cachedText, false);
                }
                scheduleComponent(componentKey, contextKey, original, false);
                return original;
            }

            scheduleComponent(componentKey, contextKey, original, canReplaceWithLiteral(original, translatable));
            return original;
        }

        if (original.getContents() instanceof LiteralContents literal) {
            if (!original.getSiblings().isEmpty()) {
                return translateLiteralComponent(original, literal, false);
            }

            String text = literal.text();
            if (isKnownTranslatedOutput(text)) return original;

            String translated = triageText(text, LITERAL_NAMESPACE, text);
            if (translated == text || translated.equals(text)) return original;

            String contextKey = buildContextKey(cacheLanguage(), LITERAL_NAMESPACE, text);
            String componentKey = componentContextKey(contextKey, original);
            Component cached = sanitizeCachedComponent(text, componentKey, componentCache.get(componentKey));
            if (cached != null) return cached;

            // triageText() can return a cache hit synchronously. Use it immediately
            // instead of waiting another async pass to build the literal component;
            // this is important for GUIs that are rebuilt right after a preload.
            MutableComponent rebuilt = literalLike(original, translated);
            cacheComponent(componentKey, rebuilt);
            TranslationSkipRegistry.skip(rebuilt);
            return rebuilt;
        }

        String text = original.getString();
        if (text == null || text.isEmpty()) return original;
        if (!hasLetter(text)) return original;
        if (text.length() < ProjectBabelCommon.config().getMinTextLength()) return original;

        String contextKey = buildContextKey(cacheLanguage(), LITERAL_NAMESPACE, text);
        String componentKey = componentContextKey(contextKey, original);
        Component cached = sanitizeCachedComponent(text, componentKey, componentCache.get(componentKey));
        if (cached != null) return cached;

        String cachedText = lookupCachedText(contextKey, text);
        if (cachedText != null && !cachedText.equals(text)) {
            Component literal = literalLike(original, cachedText);
            cacheComponent(componentKey, literal);
            TranslationSkipRegistry.skip(literal);
            return literal;
        }

        if (TranslationSkipRegistry.shouldSkip(text)) return original;
        if (isKnownTranslatedOutput(text)) return original;

        scheduleComponent(componentKey, contextKey, original, true);
        return original;
    }

    public String triageString(String text) {
        return triageText(text, LITERAL_NAMESPACE, text);
    }

    public Component triageComponentTreeCacheOnly(Component original) {
        if (original == null) return null;
        if (!canRun()) return original;
        if (TranslationSkipRegistry.shouldSkipIdentity(original)) return original;

        if (original.getContents() instanceof TranslatableContents translatable) {
            String key = translatable.getKey();
            if (key == null || key.isEmpty()) return original;

            String contextKey = buildContextKey(cacheLanguage(), extractNamespace(key), key);
            String componentKey = componentContextKey(contextKey, original);
            Component cached = sanitizeCachedComponent(original.getString(), componentKey, componentCache.get(componentKey));
            if (cached != null) return cached;

            String cachedText = findCachedTranslatableText(original, translatable, contextKey);
            boolean cachedTextChanged = isChangedText(original.getString(), cachedText);

            // Cache-only must stay lightweight. Avoid hasValidNativeTranslation(),
            // because that can run language detection for modded language keys.
            // Vanilla/native keys are left intact here; only cached args/siblings
            // are allowed to change. Explicit compat keys may still consume an
            // already-cached replacement below.
            if (hasImmediateNativeTranslation(key) && !shouldPreferExplicitCache(key)) {
                Object[] translatedArgs = translateArgsCacheOnly(key, translatable.getArgs());
                Component[] translatedSiblings = translateSiblingsCacheOnly(original);
                if (translatedArgs == null && translatedSiblings == null) return original;

                MutableComponent rebuilt = Component.translatable(
                    key,
                    translatedArgs == null ? translatable.getArgs() : translatedArgs
                ).withStyle(original.getStyle() == null ? Style.EMPTY : original.getStyle());
                appendSiblings(rebuilt, translatedSiblings, original);
                return rebuilt;
            }

            if (cachedTextChanged && shouldPreferExplicitCache(key)) {
                if (canReplaceWithLiteral(original, translatable)) {
                    return literalLike(original, cachedText);
                }
                if (canReplaceBaseWithLiteral(original, translatable)) {
                    return literalBaseWithSiblings(original, cachedText, true);
                }
            }

            if (cachedTextChanged && canReplaceWithLiteral(original, translatable)) {
                return literalLike(original, cachedText);
            }
            if (cachedTextChanged && canReplaceBaseWithLiteral(original, translatable)) {
                return literalBaseWithSiblings(original, cachedText, true);
            }

            Object[] translatedArgs = translateArgsCacheOnly(key, translatable.getArgs());
            Component[] translatedSiblings = translateSiblingsCacheOnly(original);
            if (translatedArgs == null && translatedSiblings == null) return original;

            MutableComponent rebuilt = Component.translatable(
                key,
                translatedArgs == null ? translatable.getArgs() : translatedArgs
            ).withStyle(original.getStyle() == null ? Style.EMPTY : original.getStyle());
            appendSiblings(rebuilt, translatedSiblings, original);
            return rebuilt;
        }

        if (original.getContents() instanceof LiteralContents literal) {
            return translateLiteralComponent(original, literal, true);
        }

        String text = original.getString();
        if (text == null || text.isEmpty()) return original;
        String contextKey = buildContextKey(cacheLanguage(), LITERAL_NAMESPACE, text);
        String componentKey = componentContextKey(contextKey, original);
        Component cached = sanitizeCachedComponent(text, componentKey, componentCache.get(componentKey));
        if (cached != null) return cached;

        String cachedText = lookupCachedText(contextKey, text);
        if (cachedText == null || cachedText.equals(text)) return original;

        Component literal = literalLike(original, cachedText);
        cacheComponent(componentKey, literal);
        TranslationSkipRegistry.skip(literal);
        return literal;
    }

    public String triageStringCacheOnly(String text) {
        if (!canRun()) return text;
        if (text == null || text.isEmpty()) return text;
        if (!hasLetter(text)) return text;
        if (text.length() < ProjectBabelCommon.config().getMinTextLength()) return text;

        String contextKey = buildContextKey(cacheLanguage(), LITERAL_NAMESPACE, text);
        String cached = lookupCachedText(contextKey, text);
        if (cached != null) return cached;

        // Strict cache-only path: do not schedule background work and do not run
        // language detection from render/widget tick. Semantic hooks are
        // responsible for warming cache before this fallback sees the text.
        if (TranslationSkipRegistry.shouldSkip(text)) return text;
        if (isKnownTranslatedOutput(text)) return text;
        return text;
    }

    public String triageLanguageLookup(String key, String resolved) {
        if (key == null || key.isEmpty()) return resolved;
        if (resolved == null || resolved.isEmpty()) return resolved;
        if (resolved.equals(key)) return resolved;
        if (isSortSensitiveLanguageKey(key)) return resolved;
        if (!canRun()) return resolved;
        if (!TextFilter.isScreenFilterBypassed() && !isInteractiveClientReady()) return resolved;
        if ("en".equals(apiLanguage())) return resolved;

        String contextKey = buildContextKey(cacheLanguage(), extractNamespace(key), key);

        String pinned = lookupPinnedDictionaryText(resolved);
        if (pinned != null) {
            cacheText(contextKey, sanitizeCachedText(resolved, pinned));
            return pinned;
        }

        if (isVanillaTranslationKey(key)) return resolved;

        String cached = sanitizeCachedText(resolved, textCache.get(contextKey));
        if (cached != null) return cached;

        scheduleText(contextKey, resolved, true, TextFilter.isScreenFilterBypassed());
        return resolved;
    }

    public String triageText(String text, String namespace, String translationKey) {
        if (!canRun()) return text;
        if (text == null || text.isEmpty()) return text;
        if (!hasLetter(text)) return text;
        if (text.length() < ProjectBabelCommon.config().getMinTextLength()) return text;

        String contextKey = buildContextKey(cacheLanguage(), namespace, translationKey);
        String pinned = lookupPinnedDictionaryText(text);
        if (pinned != null) {
            cacheText(contextKey, sanitizeCachedText(text, pinned));
            return pinned;
        }

        String cached = lookupCachedText(contextKey, text);
        if (cached != null) return cached;

        if (TranslationSkipRegistry.shouldSkip(text)) return text;
        if (isKnownTranslatedOutput(text)) return text;
        if (isAlreadyTargetLanguageText(text)) return text;

        scheduleText(contextKey, text, false, TextFilter.isScreenFilterBypassed());
        return text;
    }

    public boolean hasCachedText(String cacheKey) {
        return textCache.containsKey(cacheKey);
    }

    public void warmText(String originalText, String translatedText) {
        if (originalText == null || originalText.isBlank()) return;
        if (translatedText == null || translatedText.isBlank()) return;
        if (translatedText.equals(originalText)
            && needsTranslation(originalText)
            && lookupPinnedDictionaryText(originalText) == null) return;

        String contextKey = buildContextKey(cacheLanguage(), LITERAL_NAMESPACE, originalText);
        cacheText(contextKey, sanitizeCachedText(originalText, translatedText));
    }

    public void warmComponent(Component original, Component translated) {
        if (original == null || translated == null) return;

        String originalText = original.getString();
        String translatedText = translated.getString();
        if (originalText == null || originalText.isBlank()) return;
        if (translatedText == null || translatedText.isBlank()) return;
        if (translatedText.equals(originalText)
            && needsTranslation(originalText)
            && lookupPinnedDictionaryText(originalText) == null) return;

        if (original.getContents() instanceof TranslatableContents translatable) {
            String key = translatable.getKey();
            if (key == null || key.isBlank()) return;

            String contextKey = buildContextKey(cacheLanguage(), extractNamespace(key), key);
            if (hasValidNativeTranslation(contextKey, key)) return;

            cacheComponent(componentContextKey(contextKey, original), translated);
            if (canReplaceWithLiteral(original, translatable)) {
                cacheText(contextKey, sanitizeCachedText(originalText, translatedText));
            }
            return;
        }

        String contextKey = buildContextKey(cacheLanguage(), LITERAL_NAMESPACE, originalText);
        cacheComponent(componentContextKey(contextKey, original), translated);
        cacheText(contextKey, sanitizeCachedText(originalText, translatedText));
    }

    public void clearContextCache() {
        generation.incrementAndGet();
        textCache.clear();
        componentCache.clear();
        nativeTranslationCache.clear();
        targetLanguageTextCache.clear();
        pending.clear();
    }

    public boolean isIdle() {
        return pending.isEmpty();
    }

    public int getPendingCount() {
        return pending.size();
    }

    private static Executor activeExecutor() {
        return PreloadAcceleration.isActive()
            ? TranslationExecutors.preload()
            : TranslationExecutors.triage(TranslationPriority.NORMAL);
    }

    private static Executor highPriorityExecutor() {
        return PreloadAcceleration.isActive()
            ? TranslationExecutors.preload()
            : TranslationExecutors.triage(TranslationPriority.HIGH);
    }

    private void scheduleText(String contextKey, String originalText, boolean nativeLookup, boolean bypassScreenFilter) {
        if (contextKey == null || originalText == null) return;
        if (textCache.containsKey(contextKey)) return;
        if (pending.putIfAbsent(contextKey, Boolean.TRUE) != null) return;

        long taskGeneration = generation.get();
        CompletableFuture
            .supplyAsync(() -> resolveText(originalText, nativeLookup, bypassScreenFilter), activeExecutor())
            .thenAccept(translated -> {
                if (translated != null && isCurrentGeneration(taskGeneration)) {
                    cacheText(contextKey, sanitizeCachedText(originalText, translated));
                }
            })
            .exceptionally(error -> {
                ProjectBabelCommon.LOGGER.debug("[projectbabel] Triage text failed: {}", error.toString());
                return null;
            })
            .whenComplete((ignored, error) -> pending.remove(contextKey));
    }

    private void scheduleComponent(String componentKey, String textContextKey, Component original, boolean cacheTextResult) {
        if (componentKey == null || textContextKey == null || original == null) return;
        if (componentCache.containsKey(componentKey)) return;

        String pendingKey = "component:" + componentKey;
        if (pending.putIfAbsent(pendingKey, Boolean.TRUE) != null) return;

        long taskGeneration = generation.get();
        CompletableFuture
            .supplyAsync(() -> TranslationPipeline.translateComponentTreeBlocking(original), activeExecutor())
            .thenAccept(translated -> {
                if (translated != null && isCurrentGeneration(taskGeneration)) {
                    cacheComponent(componentKey, translated);
                    String translatedText = translated.getString();
                    if (cacheTextResult && translatedText != null && !translatedText.isBlank()) {
                        cacheText(textContextKey, sanitizeCachedText(original.getString(), translatedText));
                    }
                }
            })
            .exceptionally(error -> {
                ProjectBabelCommon.LOGGER.debug("[projectbabel] Triage component failed: {}", error.toString());
                return null;
            })
            .whenComplete((ignored, error) -> pending.remove(pendingKey));
    }

    private void scheduleLiteralComponent(String componentKey, Component original, String translatedText) {
        if (componentKey == null || original == null || translatedText == null) return;
        if (componentCache.containsKey(componentKey)) return;

        String pendingKey = "literal:" + componentKey;
        if (pending.putIfAbsent(pendingKey, Boolean.TRUE) != null) return;

        long taskGeneration = generation.get();
        CompletableFuture
            .supplyAsync(() -> literalLike(original, translatedText), activeExecutor())
            .thenAccept(component -> {
                if (isCurrentGeneration(taskGeneration)) {
                    cacheComponent(componentKey, component);
                }
            })
            .whenComplete((ignored, error) -> pending.remove(pendingKey));
    }

    private String resolveText(String text, boolean nativeLookup, boolean bypassScreenFilter) {
        if (bypassScreenFilter) {
            try (TextFilter.ScreenFilterBypass ignored = TextFilter.bypassScreenFilter()) {
                return resolveTextInternal(text, nativeLookup);
            }
        }
        return resolveTextInternal(text, nativeLookup);
    }

    private String resolveTextInternal(String text, boolean nativeLookup) {
        if (text == null || text.isBlank()) return text;

        String plain = TextFormatUtils.stripFormatting(text);
        if (plain == null) return text;
        plain = plain.trim();
        if (!TextFilter.shouldTranslate(plain)) return text;

        TranslationManager manager = TranslationManager.getInstance();
        String target = apiLanguage();
        String universal = UniversalTermsDictionary.getInstance().lookupExact(plain, ProjectBabelCommon.config().getSourceLang(), target);
        if (universal != null && !universal.isBlank()) {
            return TextFormatUtils.preserveEdgeWhitespace(text, universal);
        }

        String dictionary = TranslationDictionary.getInstance().lookupExact(plain);
        if (dictionary != null && !dictionary.isBlank()) {
            return TextFormatUtils.preserveEdgeWhitespace(text, dictionary);
        }

        String cached = manager.getCachedTranslation(text, ProjectBabelCommon.config().getSourceLang(), target);
        if (cached == null || cached.isBlank()) {
            cached = manager.getCachedTranslationAnySource(text, target);
        }
        if (cached != null && !cached.isBlank()) {
            return TextFormatUtils.preserveEdgeWhitespace(text, cached);
        }

        if (nativeLookup) {
            cached = manager.getCachedTranslation(plain, ProjectBabelCommon.config().getSourceLang(), target);
            if (cached == null || cached.isBlank()) {
                cached = manager.getCachedTranslationAnySource(plain, target);
            }
            if (cached != null && !cached.isBlank()) {
                return TextFormatUtils.preserveEdgeWhitespace(text, cached);
            }
        }

        if (manager.isAlreadyTranslatedValue(text)) return text;

        String detected = LanguageDetector.detectLanguage(plain);
        if (target.equals(detected)) return text;

        String source = detected.equals("unknown")
            ? ProjectBabelCommon.config().getSourceLang()
            : detected;

        cached = manager.getCachedTranslation(text, source, target);
        if (cached != null) {
            return TextFormatUtils.preserveEdgeWhitespace(text, cached);
        }

        if (nativeLookup) {
            cached = manager.getCachedTranslation(plain, source, target);
            if (cached != null) {
                return TextFormatUtils.preserveEdgeWhitespace(text, cached);
            }
        }

        String translated = manager.getTranslationPreservingFormatBlocking(text, source, target);
        if (translated == null || translated.isBlank()) return text;
        return TextFormatUtils.preserveEdgeWhitespace(text, translated);
    }


    private Component translateLiteralComponent(Component original, LiteralContents literal, boolean cacheOnly) {
        String text = literal.text();
        String translatedText;
        if (cacheOnly) {
            translatedText = lookupCachedText(buildContextKey(cacheLanguage(), LITERAL_NAMESPACE, text), text);
        } else {
            translatedText = triageText(text, LITERAL_NAMESPACE, text);
        }

        boolean textChanged = translatedText != null && !translatedText.equals(text);
        Component[] translatedSiblings = cacheOnly
            ? translateSiblingsCacheOnly(original)
            : translateSiblings(original);
        if (!textChanged && translatedSiblings == null) return original;

        MutableComponent rebuilt = Component.literal(textChanged ? translatedText : text)
            .withStyle(original.getStyle() == null ? Style.EMPTY : original.getStyle());
        appendSiblings(rebuilt, translatedSiblings, original);
        if (textChanged) TranslationSkipRegistry.skip(rebuilt);
        return rebuilt;
    }

    private Component[] translateSiblings(Component original) {
        if (original.getSiblings().isEmpty()) return null;

        Component[] translatedSiblings = null;
        for (int i = 0; i < original.getSiblings().size(); i++) {
            Component sibling = original.getSiblings().get(i);
            Component translated = triageComponentTree(sibling);
            if (translated != sibling) {
                if (translatedSiblings == null) {
                    translatedSiblings = original.getSiblings().toArray(Component[]::new);
                }
                translatedSiblings[i] = translated;
            }
        }
        return translatedSiblings;
    }

    private Component[] translateSiblingsCacheOnly(Component original) {
        if (original.getSiblings().isEmpty()) return null;

        Component[] translatedSiblings = null;
        for (int i = 0; i < original.getSiblings().size(); i++) {
            Component sibling = original.getSiblings().get(i);
            Component translated = triageComponentTreeCacheOnly(sibling);
            if (translated != sibling) {
                if (translatedSiblings == null) {
                    translatedSiblings = original.getSiblings().toArray(Component[]::new);
                }
                translatedSiblings[i] = translated;
            }
        }
        return translatedSiblings;
    }

    private Object[] translateArgsCacheOnly(String key, Object[] args) {
        if (args == null || args.length == 0) return null;

        Object[] translatedArgs = null;
        for (int i = 0; i < args.length; i++) {
            if (isChatSenderArgument(key, i)) continue;

            Object arg = args[i];
            Object translated = translateArgCacheOnly(arg);
            if (translated != arg) {
                if (translatedArgs == null) translatedArgs = args.clone();
                translatedArgs[i] = translated;
            }
        }
        return translatedArgs;
    }

    private Object translateArgCacheOnly(Object arg) {
        if (arg instanceof Component component) {
            return triageComponentTreeCacheOnly(component);
        }
        if (arg instanceof String string) {
            String contextKey = buildContextKey(cacheLanguage(), LITERAL_NAMESPACE, string);
            String cached = lookupCachedText(contextKey, string);
            return cached == null || cached.equals(string) ? arg : cached;
        }
        return arg;
    }

    private MutableComponent literalBaseWithSiblings(Component original, String translatedBase, boolean cacheOnly) {
        MutableComponent rebuilt = literalLike(original, translatedBase);
        for (Component sibling : original.getSiblings()) {
            rebuilt.append(cacheOnly ? triageComponentTreeCacheOnly(sibling) : triageComponentTree(sibling));
        }
        return rebuilt;
    }

    private static void appendSiblings(
        MutableComponent rebuilt,
        Component[] translatedSiblings,
        Component original
    ) {
        if (translatedSiblings != null) {
            for (Component sibling : translatedSiblings) {
                rebuilt.append(sibling);
            }
            return;
        }

        for (Component sibling : original.getSiblings()) {
            rebuilt.append(sibling);
        }
    }

    private String lookupPinnedDictionaryText(String text) {
        if (text == null || text.isBlank()) return null;

        String plain = TextFormatUtils.stripFormatting(text);
        if (plain == null || plain.isBlank()) return null;
        plain = plain.trim();

        String target = apiLanguage();

        // O glossario/dicionario e uma camada de autoridade. Se o usuario colocou
        // "Termo": "Termo" ou uma traducao ainda em ingles, isso significa
        // manter exatamente aquele valor e nunca mandar para a API/cache antigo.
        String universal = UniversalTermsDictionary.getInstance()
            .lookupExact(plain, ProjectBabelCommon.config().getSourceLang(), target);
        if (universal != null && !universal.isBlank()) {
            return TextFormatUtils.preserveEdgeWhitespace(text, universal);
        }

        String dictionary = TranslationDictionary.getInstance().lookupExact(plain);
        if (dictionary != null && !dictionary.isBlank()) {
            return TextFormatUtils.preserveEdgeWhitespace(text, dictionary);
        }

        return null;
    }

    private String lookupCachedText(String contextKey, String text) {
        String pinned = lookupPinnedDictionaryText(text);
        if (pinned != null) {
            cacheText(contextKey, sanitizeCachedText(text, pinned));
            return pinned;
        }

        String local = sanitizeCachedText(text, textCache.get(contextKey));
        if (local != null && !local.equals(text)) return local;
        if (text == null || text.isBlank()) return null;

        String external = TranslationManager.getInstance()
            .getCachedTranslation(text, ProjectBabelCommon.config().getSourceLang(), apiLanguage());
        if (external == null || external.isBlank()) {
            external = TranslationManager.getInstance()
                .getCachedTranslationAnySource(text, apiLanguage());
        }
        if (external == null || external.isBlank()) {
            if (isKnownTranslatedOutput(text)) return text;
            return local;
        }

        external = sanitizeCachedText(text, TextFormatUtils.preserveEdgeWhitespace(text, external));
        cacheText(contextKey, external);
        return external;
    }

    private String findCachedTranslatableText(
        Component original,
        TranslatableContents translatable,
        String contextKey
    ) {
        if (canReplaceWithLiteral(original, translatable)) {
            return lookupCachedText(contextKey, original.getString());
        }

        if (canReplaceBaseWithLiteral(original, translatable)) {
            String baseText = resolveTranslatableBaseText(translatable.getKey());
            return lookupCachedText(contextKey, baseText);
        }

        return sanitizeCachedText(original.getString(), textCache.get(contextKey));
    }

    private static String resolveTranslatableBaseText(String key) {
        if (key == null || key.isBlank()) return "";
        try {
            String resolved = I18n.get(key);
            return resolved == null || resolved.isBlank() ? key : resolved;
        } catch (Exception ignored) {
            return key;
        }
    }

    private boolean canRun() {
        return ProjectBabelCommon.config().isEnabled() && LanguageDetector.shouldModBeActive();
    }

    private boolean isInteractiveClientReady() {
        try {
            Minecraft mc = Minecraft.getInstance();
            return mc != null && mc.player != null && mc.level != null;
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean needsTranslation(String text) {
        String plain = TextFormatUtils.stripFormatting(text);
        if (plain == null) return false;
        plain = plain.trim();
        if (!TextFilter.shouldTranslate(plain)) return false;
        String detected = LanguageDetector.detectLanguage(plain);
        return !apiLanguage().equals(detected);
    }

    private boolean isAlreadyTargetLanguageText(String text) {
        if (text == null || text.isBlank()) return false;

        Boolean cached = targetLanguageTextCache.get(text);
        if (cached != null) return cached;

        if (targetLanguageTextCache.size() >= 8192) {
            targetLanguageTextCache.clear();
        }

        boolean result = false;
        try {
            String plain = TextFormatUtils.stripFormatting(text);
            if (plain != null) {
                plain = plain.trim();
                if (TextFilter.shouldTranslate(plain)) {
                    result = apiLanguage().equals(LanguageDetector.detectLanguage(plain));
                }
            }
        } catch (Exception ignored) {
            result = false;
        }

        targetLanguageTextCache.put(text, result);
        return result;
    }

    private boolean isKnownTranslatedOutput(String text) {
        if (text == null || text.isBlank()) return false;
        try {
            boolean known = TranslationManager.getInstance().isAlreadyTranslatedValue(text);
            if (known) {
                TranslationSkipRegistry.skipText(text);
            }
            return known;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static String sanitizeCachedText(String original, String cached) {
        if (cached == null) return null;
        cached = TextFormatUtils.collapseExactDuplicateTranslation(original, cached);
        cached = TextFormatUtils.collapseRepeatedTranslation(cached);
        cached = TextFormatUtils.preserveTrailingRomanNumeral(original, cached);
        return TextFormatUtils.preserveEdgeWhitespace(original, cached);
    }

    private static Component sanitizeCachedComponent(String original, String componentKey, Component cached) {
        if (cached == null) return null;
        String text = cached.getString();
        String sanitized = sanitizeCachedText(original, text);
        if (sanitized == null || sanitized.equals(text) || !cached.getSiblings().isEmpty()) {
            return cached;
        }

        Component cleaned = literalLike(cached, sanitized);
        cacheComponent(componentKey, cleaned);
        TranslationSkipRegistry.skip(cleaned);
        return cleaned;
    }

    private boolean isCurrentGeneration(long taskGeneration) {
        return generation.get() == taskGeneration;
    }

    private String cacheLanguage() {
        refreshLanguageCache();
        return lastCacheLanguage;
    }

    private String apiLanguage() {
        refreshLanguageCache();
        return lastApiLanguage;
    }

    private void refreshLanguageCache() {
        String current = currentLanguageCode();
        String configuredTarget = ProjectBabelCommon.config().getTargetLang();
        if (configuredTarget == null) configuredTarget = "";
        boolean followClientLanguage = ProjectBabelCommon.config().isFollowClientLanguage();
        if (current.equals(lastClientLanguage)
            && configuredTarget.equals(lastConfiguredTarget)
            && followClientLanguage == lastFollowClientLanguage) {
            return;
        }

        lastClientLanguage = current;
        lastConfiguredTarget = configuredTarget;
        lastFollowClientLanguage = followClientLanguage;
        lastCacheLanguage = followClientLanguage ? current : normalizeClientCode(configuredTarget);
        lastApiLanguage = normalizeLanguage(
            followClientLanguage
                ? current
                : configuredTarget
        );
    }

    private String currentLanguageCode() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.options != null && mc.options.languageCode != null) {
                return normalizeClientCode(mc.options.languageCode);
            }
        } catch (Exception ignored) {}
        return normalizeClientCode(ProjectBabelCommon.config().getTargetLang());
    }

    private static String normalizeClientCode(String languageCode) {
        if (languageCode == null || languageCode.isBlank()) return "en";
        return languageCode.toLowerCase(Locale.ROOT).replace('-', '_');
    }

    private static String normalizeLanguage(String languageCode) {
        if (languageCode == null || languageCode.isBlank()) return "en";

        String normalized = languageCode.toLowerCase(Locale.ROOT).replace('-', '_');
        int separator = normalized.indexOf('_');
        return separator > 0 ? normalized.substring(0, separator) : normalized;
    }

    private static String buildContextKey(String targetLang, String namespace, String translationKey) {
        return targetLang + ':' + namespace + ':' + translationKey;
    }

    private static String componentContextKey(String contextKey, Component original) {
        Style style = original.getStyle();
        String base = contextKey + ":style:" + (style == null ? 0 : style.hashCode());
        if (!needsResolvedComponentFingerprint(original)) return base;

        String resolved = original.getString();
        int length = resolved == null ? 0 : resolved.length();
        int hash = resolved == null ? 0 : resolved.hashCode();
        return base + ":resolved:" + length + ':' + hash;
    }


    private boolean hasImmediateNativeTranslation(String key) {
        try {
            if (key == null || key.isBlank()) return false;
            if (!I18n.exists(key)) return false;
            String translated = I18n.get(key);
            return translated != null && !translated.isBlank() && !translated.equals(key);
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean hasValidNativeTranslation(String contextKey, String key) {
        Boolean cached = nativeTranslationCache.get(contextKey);
        if (cached != null) return cached;

        boolean valid = computeNativeTranslationValidity(key);
        nativeTranslationCache.put(contextKey, valid);
        return valid;
    }

    private static boolean shouldPreferExplicitCache(String key) {
        return key != null && (!isVanillaTranslationKey(key) || isEnchantmentDescriptionKey(key));
    }

    private static boolean isChangedText(String original, String translated) {
        return translated != null && original != null && !translated.equals(original);
    }

    private boolean computeNativeTranslationValidity(String key) {
        try {
            if (!I18n.exists(key)) return false;

            String translated = I18n.get(key);
            if (translated == null || translated.isBlank() || translated.equals(key)) return false;

            String target = apiLanguage();
            if ("en".equals(target)) return true;
            if (isVanillaTranslationKey(key) && !isEnchantmentDescriptionKey(key)) return true;

            if (!TextFilter.shouldTranslate(translated)) return true;

            String plain = TextFormatUtils.stripFormatting(translated);
            if (plain == null) return true;
            plain = plain.trim();
            if (!TextFilter.shouldTranslate(plain)) return true;

            return target.equals(LanguageDetector.detectLanguage(plain));
        } catch (Exception ignored) {
            return false;
        }
    }

    private static void cacheText(String contextKey, String value) {
        TranslationTriageManager instance = INSTANCE;
        if (instance.textCache.size() >= MAX_CONTEXT_CACHE_SIZE) {
            instance.textCache.clear();
        }
        instance.textCache.put(contextKey, value);
    }

    private static void cacheComponent(String contextKey, Component value) {
        TranslationTriageManager instance = INSTANCE;
        if (instance.componentCache.size() >= MAX_COMPONENT_CACHE_SIZE) {
            instance.componentCache.clear();
        }
        instance.componentCache.put(contextKey, value);
    }

    private static MutableComponent literalLike(Component original, String translated) {
        Style style = original.getStyle();
        return Component.literal(translated).withStyle(style == null ? Style.EMPTY : style);
    }

    private static boolean canReplaceWithLiteral(Component original, TranslatableContents translatable) {
        return original.getSiblings().isEmpty() && translatable.getArgs().length == 0;
    }

    private static boolean canReplaceBaseWithLiteral(Component original, TranslatableContents translatable) {
        return !original.getSiblings().isEmpty() && translatable.getArgs().length == 0;
    }

    private static boolean needsResolvedComponentFingerprint(Component original) {
        if (original == null) return false;
        if (!original.getSiblings().isEmpty()) return true;
        if (original.getContents() instanceof TranslatableContents translatable) {
            return translatable.getArgs().length > 0;
        }
        return false;
    }

    private static boolean isChatSenderArgument(String key, int index) {
        return index == 0 && key != null && key.startsWith("chat.type.");
    }

    private static boolean hasLetter(String text) {
        for (int i = 0; i < text.length(); i++) {
            if (Character.isLetter(text.charAt(i))) return true;
        }
        return false;
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

    private static boolean isVanillaTranslationKey(String key) {
        return key != null && "minecraft".equals(extractNamespace(key));
    }


    private static boolean isEnchantmentDescriptionKey(String key) {
        return key != null
            && key.startsWith("enchantment.")
            && (key.endsWith(".desc") || key.endsWith(".description") || key.endsWith(".tooltip"));
    }

    private static boolean isSortSensitiveLanguageKey(String key) {
        return key != null && key.startsWith("attribute.");
    }
}
