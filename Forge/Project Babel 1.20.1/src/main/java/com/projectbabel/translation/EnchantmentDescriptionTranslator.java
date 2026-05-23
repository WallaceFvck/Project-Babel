package com.projectbabel.translation;

import com.projectbabel.ProjectBabelMod;
import com.projectbabel.config.AutoTranslateConfig;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.TranslatableContents;

import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/**
 * Caminho especializado para descrições de encantamentos.
 *
 * Regras importantes para tooltip/render thread:
 * - nunca chama detector de idioma na thread de renderização;
 * - nunca chama API/tradução bloqueante na thread de renderização;
 * - texto nativo no idioma alvo é marcado como no-op e não cai no pipeline genérico;
 * - textos em inglês são traduzidos fora da thread de renderização e reaproveitados via cache local.
 */
public final class EnchantmentDescriptionTranslator {

    private static final int MAX_CACHE_SIZE = 32_768;

    private static final ConcurrentHashMap<String, Component> COMPONENT_CACHE = new ConcurrentHashMap<>(4096);
    private static final Set<String> NO_TRANSLATION_CACHE = ConcurrentHashMap.newKeySet(4096);
    private static final ConcurrentHashMap<String, Long> COOLDOWN_UNTIL = new ConcurrentHashMap<>(4096);
    private static final Set<String> PENDING = ConcurrentHashMap.newKeySet(4096);
    private static final long RETRY_COOLDOWN_MS = 15_000L;

    private EnchantmentDescriptionTranslator() {
    }

    public static Component translate(Component original) {
        if (original == null || !AutoTranslateConfig.isEnabled()) return original;
        if (!LanguageDetector.shouldModBeActive()) return original;
        if (TextFilter.isDebugScreenOpen()) return original;
        if (!isDescriptionComponent(original)) return original;

        String text = original.getString();
        if (text == null || text.isBlank()) return original;

        String cacheKey = cacheKey(original, text);
        Component cached = COMPONENT_CACHE.get(cacheKey);
        if (cached != null) return cached;
        if (NO_TRANSLATION_CACHE.contains(cacheKey)) return original;
        if (isCoolingDown(cacheKey)) return original;

        String target = LanguageDetector.getTargetLanguageForApi();
        if (isNativeTargetDescription(original, text, target)) {
            rememberNoTranslation(cacheKey, original, text);
            return original;
        }

        String cachedText = getCachedExternalTranslation(text, target);
        if (cachedText != null && !cachedText.isBlank()) {
            if (!cachedText.equals(text)) {
                Component rebuilt = rebuild(original, text, cachedText);
                rememberComponent(cacheKey, rebuilt);
                return rebuilt;
            }
            rememberNoTranslation(cacheKey, original, text);
            return original;
        }

        schedule(cacheKey, original, text, target);
        return original;
    }

    public static boolean isDescriptionComponent(Component component) {
        if (component == null) return false;

        if (component.getContents() instanceof TranslatableContents translatable) {
            if (isDescriptionKey(translatable.getKey())) return true;

            for (Object arg : translatable.getArgs()) {
                if (arg instanceof Component argComponent && isDescriptionComponent(argComponent)) {
                    return true;
                }
            }
        }

        for (Component sibling : component.getSiblings()) {
            if (isDescriptionComponent(sibling)) return true;
        }

        return false;
    }

    public static void clear() {
        COMPONENT_CACHE.clear();
        NO_TRANSLATION_CACHE.clear();
        COOLDOWN_UNTIL.clear();
        PENDING.clear();
    }

    private static void schedule(String cacheKey, Component original, String text, String target) {
        if (!PENDING.add(cacheKey)) return;
        trimIfNeeded();

        CompletableFuture
            .supplyAsync(() -> resolveText(text, target), activeExecutor())
            .thenAccept(result -> {
                if (result == null || result.text == null || result.text.isBlank()) {
                    rememberCooldown(cacheKey);
                    return;
                }
                if (result.text.equals(text)) {
                    if (result.permanentNoTranslation) {
                        rememberNoTranslation(cacheKey, original, text);
                    } else {
                        rememberCooldown(cacheKey);
                    }
                    return;
                }

                Component rebuilt = rebuild(original, text, result.text);
                rememberComponent(cacheKey, rebuilt);
            })
            .exceptionally(error -> {
                ProjectBabelMod.LOGGER.debug(
                    "[projectbabel] Enchantment description translation failed: {}",
                    error.toString()
                );
                rememberCooldown(cacheKey);
                return null;
            })
            .whenComplete((ignored, error) -> PENDING.remove(cacheKey));
    }

    private static ResolveResult resolveText(String text, String requestedTarget) {
        if (text == null || text.isBlank()) return ResolveResult.permanent(text);

        try (TextFilter.ScreenFilterBypass ignored = TextFilter.bypassScreenFilter()) {
            String target = requestedTarget == null || requestedTarget.isBlank()
                ? LanguageDetector.getTargetLanguageForApi()
                : requestedTarget;

            String cached = getCachedExternalTranslation(text, target);
            if (cached != null && !cached.isBlank()) return ResolveResult.permanent(cached);

            String plain = TextFormatUtils.stripFormatting(text);
            if (plain == null) return ResolveResult.permanent(text);
            plain = plain.trim();
            if (!TextFilter.shouldTranslate(plain)) return ResolveResult.permanent(text);

            String detected = LanguageDetector.detectLanguage(plain);
            if (target.equals(detected)) return ResolveResult.permanent(text);

            String source = detected.equals("unknown")
                ? AutoTranslateConfig.getSourceLang()
                : detected;

            cached = TranslationManager.getInstance().getCachedTranslation(text, source, target);
            if (cached == null || cached.isBlank()) {
                cached = TranslationManager.getInstance().getCachedTranslationAnySource(text, target);
            }
            if (cached != null && !cached.isBlank()) {
                return ResolveResult.permanent(TextFormatUtils.preserveEdgeWhitespace(text, cached));
            }

            String translated = TranslationManager.getInstance()
                .getTranslationPreservingFormatBlockingBypassOutputGuard(text, source, target);
            return ResolveResult.retryable(translated);
        } catch (Exception e) {
            ProjectBabelMod.LOGGER.debug(
                "[projectbabel] Enchantment description resolve failed for '{}': {}",
                text,
                e.toString()
            );
            return ResolveResult.retryable(text);
        }
    }

    private static Executor activeExecutor() {
        return PreloadAcceleration.isActive()
            ? TranslationExecutors.preload()
            : TranslationExecutors.triage();
    }

    private static String getCachedExternalTranslation(String text, String target) {
        if (text == null || text.isBlank()) return null;
        TranslationManager manager = TranslationManager.getInstance();
        String cached = manager.getCachedTranslation(text, AutoTranslateConfig.getSourceLang(), target);
        if (cached == null || cached.isBlank()) {
            cached = manager.getCachedTranslationAnySource(text, target);
        }
        if (cached == null || cached.isBlank()) return null;
        return sanitize(text, cached);
    }

    private static Component rebuild(Component original, String originalText, String translatedText) {
        String sanitized = sanitize(originalText, translatedText);
        if (sanitized == null || sanitized.isBlank() || sanitized.equals(originalText)) return original;

        MutableComponent rebuilt = Component.literal(sanitized)
            .withStyle(original.getStyle() == null ? Style.EMPTY : original.getStyle());
        TranslationSkipRegistry.skip(rebuilt);
        TranslationSkipRegistry.skipText(sanitized);
        return rebuilt;
    }

    private static String sanitize(String original, String translated) {
        if (translated == null) return null;
        translated = TextFormatUtils.postProcess(translated);
        translated = TextFormatUtils.collapseExactDuplicateTranslation(original, translated);
        translated = TextFormatUtils.collapseRepeatedTranslation(translated);
        translated = TextFormatUtils.preserveTrailingRomanNumeral(original, translated);
        return TextFormatUtils.preserveEdgeWhitespace(original, translated);
    }

    private static void rememberComponent(String cacheKey, Component component) {
        if (component == null) return;
        trimIfNeeded();
        COMPONENT_CACHE.put(cacheKey, component);
        NO_TRANSLATION_CACHE.remove(cacheKey);
        COOLDOWN_UNTIL.remove(cacheKey);
        TranslationSkipRegistry.skip(component);
    }

    private static void rememberNoTranslation(String cacheKey, Component original, String text) {
        trimIfNeeded();
        NO_TRANSLATION_CACHE.add(cacheKey);
        COOLDOWN_UNTIL.remove(cacheKey);
        if (original != null) TranslationSkipRegistry.skip(original);
        if (text != null && !text.isBlank()) TranslationSkipRegistry.skipText(text);
    }


    private static boolean isCoolingDown(String cacheKey) {
        Long retryAt = COOLDOWN_UNTIL.get(cacheKey);
        if (retryAt == null) return false;
        if (retryAt > System.currentTimeMillis()) return true;
        COOLDOWN_UNTIL.remove(cacheKey, retryAt);
        return false;
    }

    private static void rememberCooldown(String cacheKey) {
        if (cacheKey == null || cacheKey.isBlank()) return;
        trimIfNeeded();
        COOLDOWN_UNTIL.put(cacheKey, System.currentTimeMillis() + RETRY_COOLDOWN_MS);
    }

    private static void trimIfNeeded() {
        if (COMPONENT_CACHE.size() + NO_TRANSLATION_CACHE.size() + COOLDOWN_UNTIL.size() + PENDING.size() <= MAX_CACHE_SIZE) return;
        COMPONENT_CACHE.clear();
        NO_TRANSLATION_CACHE.clear();
        COOLDOWN_UNTIL.clear();
        PENDING.clear();
    }

    private static boolean isNativeTargetDescription(Component original, String text, String target) {
        if (target == null || target.isBlank()) return false;
        if ("en".equals(target)) return true;

        String key = firstDescriptionKey(original);
        if (key == null || key.isBlank()) return false;
        if (!I18n.exists(key)) return false;

        String resolved;
        try {
            resolved = I18n.get(key);
        } catch (Exception ignored) {
            return false;
        }

        if (resolved == null || resolved.isBlank() || resolved.equals(key)) return false;
        if (!normalizeText(resolved).equals(normalizeText(text))) return false;

        return looksLikeTargetLanguageFast(text, target);
    }

    private static boolean looksLikeTargetLanguageFast(String text, String target) {
        String normalizedTarget = target.toLowerCase(Locale.ROOT);
        if (normalizedTarget.startsWith("pt")) return looksPortuguese(text);

        // Para outros idiomas, manter o caminho estritamente não-bloqueante: se não
        // dá para provar barato que já está no idioma alvo, deixa a checagem pesada
        // para a tarefa assíncrona.
        return false;
    }

    private static boolean looksPortuguese(String text) {
        if (text == null || text.isBlank()) return false;

        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.matches(".*[áàâãéêíóôõúç].*")) return true;
        if (lower.contains("ção") || lower.contains("ções")) return true;

        int score = 0;
        String padded = ' ' + lower.replace('\n', ' ').replace('\r', ' ') + ' ';
        String[] markers = {
            " de ", " da ", " do ", " das ", " dos ", " que ", " para ",
            " por ", " com ", " sem ", " uma ", " um ", " ao ", " aos ",
            " nas ", " nos ", " seu ", " sua ", " quando ", " enquanto ",
            " aumenta ", " reduzir ", " reduz ", " concede ", " causa ",
            " dano ", " nível ", " chance ", " velocidade ", " alvo ",
            " inimigo ", " ferramenta ", " armadura ", " jogador ", " encantamento "
        };

        for (String marker : markers) {
            if (padded.contains(marker) && ++score >= 2) return true;
        }

        return false;
    }

    private static String cacheKey(Component original, String text) {
        String key = firstDescriptionKey(original);
        Style style = original.getStyle();
        String target = LanguageDetector.getTargetLanguageForApi();
        return target + '|'
            + (key == null ? "literal" : key) + '|'
            + text.length() + ':' + text.hashCode() + '|'
            + (style == null ? 0 : style.hashCode());
    }

    private static String firstDescriptionKey(Component component) {
        if (component == null) return null;

        if (component.getContents() instanceof TranslatableContents translatable) {
            if (isDescriptionKey(translatable.getKey())) return translatable.getKey();

            for (Object arg : translatable.getArgs()) {
                if (arg instanceof Component argComponent) {
                    String key = firstDescriptionKey(argComponent);
                    if (key != null) return key;
                }
            }
        }

        for (Component sibling : component.getSiblings()) {
            String key = firstDescriptionKey(sibling);
            if (key != null) return key;
        }

        return null;
    }

    private static boolean isDescriptionKey(String key) {
        if (key == null || key.isBlank()) return false;
        return key.startsWith("enchantment.")
            && (key.endsWith(".desc") || key.endsWith(".description"));
    }

    private static String normalizeText(String text) {
        return text == null ? "" : text.trim().replaceAll("\\s+", " ");
    }

    private static final class ResolveResult {
        private final String text;
        private final boolean permanentNoTranslation;

        private ResolveResult(String text, boolean permanentNoTranslation) {
            this.text = text;
            this.permanentNoTranslation = permanentNoTranslation;
        }

        private static ResolveResult permanent(String text) {
            return new ResolveResult(text, true);
        }

        private static ResolveResult retryable(String text) {
            return new ResolveResult(text, false);
        }
    }

}
