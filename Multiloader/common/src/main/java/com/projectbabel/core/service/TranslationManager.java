package com.projectbabel.core.service;

import com.projectbabel.ProjectBabelCommon;
import com.projectbabel.core.engine.GoogleTranslateEngine;
import com.projectbabel.core.engine.LingvaEngine;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import com.projectbabel.core.text.LanguageDetector;
import com.projectbabel.core.schedule.PreloadAcceleration;
import com.projectbabel.core.text.TextFilter;
import com.projectbabel.core.text.TextFormatUtils;
import com.projectbabel.core.cache.TranslationCache;
import com.projectbabel.core.dictionary.TranslationDictionary;
import com.projectbabel.core.schedule.TranslationExecutors;
import com.projectbabel.core.pipeline.TranslationTriageManager;
import com.projectbabel.core.dictionary.UniversalTermsDictionary;
/**
 * Coordena todo o fluxo de traducao.
 *
 * FIXES NESTA VERSÃO:
 *
 * 1. LOOP INFINITO por texto: quando uma task falha,
 *    ela saía do pendingKeys e era imediatamente re-enfileirada na próxima frame.
 *    Fix: failedKeys com TTL de 10s. Textos que falharam ficam em cooldown
 *    antes de serem re-tentados. Após o cooldown, voltam a ser elegíveis.
 *
 * 2. isAlreadyTranslated não funcionava para "toro de carvalho":
 *    o translatedValues.add() em put() salva em lowercase mas o texto
 *    que chega em getTranslation() pode ter maiúsculas diferentes.
 *    Agora comparamos sempre .strip().toLowerCase().
 *
 * 3. LingvaEngine: se Google e fallback falharem para um texto,
 *    o texto entra em failedKeys e não é re-tentado por 10s.
 */
public class TranslationManager {

    private static volatile TranslationManager INSTANCE;

    // Cooldown após falha: texto não é re-enfileirado por este período
    private static final long FAILED_KEY_TTL_MS = 2_000L;

    private final TranslationCache     cache;
    private final GoogleTranslateEngine google;
    private final LingvaEngine         lingva;

    // Textos atualmente em processamento
    private final Set<String>                            pendingKeys;
    // Textos que falharam recentemente — chave: pendingKey, valor: timestamp de expiração
    private final Map<String, Long>                      failedKeys;
    private final PriorityBlockingQueue<TranslationTask> taskQueue;
    private final AtomicBoolean                          shuttingDown   = new AtomicBoolean(false);
    private static final class TranslationTask implements Comparable<TranslationTask> {
        final String text, src, tgt, pendingKey;
        final int priority;
        TranslationTask(String t, String s, String g, String k, int p) {
            text = t; src = s; tgt = g; pendingKey = k; priority = p;
        }
        @Override public int compareTo(TranslationTask o) { return Integer.compare(priority, o.priority); }
    }

    private TranslationManager() {
        this.cache          = new TranslationCache();
        this.google         = new GoogleTranslateEngine();
        this.lingva         = new LingvaEngine();

        this.pendingKeys      = ConcurrentHashMap.newKeySet();
        this.failedKeys       = new ConcurrentHashMap<>();
        this.taskQueue        = new PriorityBlockingQueue<>(262144);
        int workerCount = TranslationExecutors.workerThreads();
        for (int i = 0; i < workerCount; i++) {
            TranslationExecutors.translationWorkers().execute(this::workerLoop);
        }

        CompletableFuture.runAsync(() -> {
            try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
            LanguageDetector.prepopulateFromGameLanguages();
        }, TranslationExecutors.io());

        ProjectBabelCommon.LOGGER.info(
            "[projectbabel] Iniciado. Engines=Google/Lingva, Workers={}, Turbo={}, LimiteAtivo={}",
            workerCount,
            ProjectBabelCommon.config().isTurboMode(),
            getActiveConcurrencyLimit()
        );
        ProjectBabelCommon.LOGGER.info("[projectbabel] Scheduler: {}", TranslationExecutors.modeSummary());
    }

    public static TranslationManager getInstance() {
        if (INSTANCE == null || INSTANCE.shuttingDown.get()) {
            synchronized (TranslationManager.class) {
                if (INSTANCE == null || INSTANCE.shuttingDown.get()) INSTANCE = new TranslationManager();
            }
        }
        return INSTANCE;
    }

    public int getActiveConcurrencyLimit() {
        return TranslationExecutors.activeTranslationLimit();
    }

    /**
     * Chamado da render thread — NUNCA bloqueia.
     * Retorna tradução do cache ou null (enfileira async se elegível).
     */
    /**
     * Traduz texto preservando códigos de formatação inline.
     *
     * Diferente do fluxo antigo "strip + prefixo", este método quebra a linha em
     * segmentos, enfileira apenas texto puro e reconstrói a saída com os códigos
     * §/&/hex/placeholders no mesmo lugar. Retorna null enquanto algum segmento
     * ainda está sendo traduzido em background.
     */
    public String getTranslationPreservingFormat(String originalText) {
        return getTranslationPreservingFormat(
            originalText,
            ProjectBabelCommon.config().getSourceLang(),
            LanguageDetector.getTargetLanguageForApi()
        );
    }

    public CompletableFuture<String> getTranslationPreservingFormatAsync(
        String originalText,
        String sourceLang,
        String targetLang
    ) {
        return CompletableFuture.supplyAsync(
            () -> getTranslationPreservingFormatBlocking(originalText, sourceLang, targetLang),
            TranslationExecutors.translationWorkers()
        );
    }

    public String getTranslationPreservingFormat(String originalText, String sourceLang, String targetLang) {
        if (hasLineBreak(originalText)) {
            return getMultilineTranslationPreservingFormat(originalText, sourceLang, targetLang);
        }
        return getSingleLineTranslationPreservingFormat(originalText, sourceLang, targetLang);
    }

    public String getTranslationPreservingFormatBlocking(String originalText, String sourceLang, String targetLang) {
        if (hasLineBreak(originalText)) {
            return getMultilineTranslationPreservingFormatBlocking(originalText, sourceLang, targetLang);
        }
        return getSingleLineTranslationPreservingFormatBlocking(originalText, sourceLang, targetLang);
    }

    /**
     * Same as the normal blocking path, but ignores the translated-output guard.
     *
     * This is intentionally narrow-use. Some mod-provided translatable keys, most
     * visibly enchantment descriptions, can be saved as "already translated" when a
     * previous session resolved them as native text. In that case the generic guard
     * prevents a later real translation even though the text is still in English.
     */
    public String getTranslationPreservingFormatBlockingBypassOutputGuard(
        String originalText,
        String sourceLang,
        String targetLang
    ) {
        if (hasLineBreak(originalText)) {
            return getMultilineTranslationPreservingFormatBlocking(originalText, sourceLang, targetLang, false);
        }
        return getSingleLineTranslationPreservingFormatBlocking(originalText, sourceLang, targetLang, false);
    }

    private String getSingleLineTranslationPreservingFormat(String originalText, String sourceLang, String targetLang) {
        if (originalText == null || originalText.isBlank()) return null;

        String plain = TextFormatUtils.stripFormatting(originalText);
        if (plain == null || !TextFilter.shouldTranslate(plain)) return null;

        // Caminho rápido para texto sem tokens de formatação/placeholders.
        if (!TextFormatUtils.hasFormattingTokens(originalText) && plain.equals(originalText.strip())) {
            String translated = getTranslation(plain, sourceLang, targetLang);
            return translated == null ? null : TextFormatUtils.preserveEdgeWhitespace(originalText, translated);
        }

        TextFormatUtils.TranslatableText prepared = TextFormatUtils.prepareForTranslation(originalText);
        if (prepared.skip()) return null;

        List<String> translatedFragments = new ArrayList<>();
        boolean allReady = true;
        boolean anyChanged = false;

        for (String fragment : prepared.getTextsForTranslation()) {
            String cleanFragment = TextFormatUtils.stripFormatting(fragment);
            if (cleanFragment == null || cleanFragment.isBlank()) {
                translatedFragments.add(fragment);
                continue;
            }

            String requestFragment = cleanFragment.strip();
            String translated = getTranslation(requestFragment, sourceLang, targetLang);
            if (translated == null || translated.equals(requestFragment)) {
                allReady = false;
                translatedFragments.add(fragment);
            } else {
                translatedFragments.add(translated);
                anyChanged = true;
            }
        }

        if (!allReady || !anyChanged) return null;
        return prepared.reconstruct(translatedFragments);
    }

    private String getSingleLineTranslationPreservingFormatBlocking(String originalText, String sourceLang, String targetLang) {
        return getSingleLineTranslationPreservingFormatBlocking(originalText, sourceLang, targetLang, true);
    }

    private String getSingleLineTranslationPreservingFormatBlocking(
        String originalText,
        String sourceLang,
        String targetLang,
        boolean respectOutputGuard
    ) {
        if (originalText == null || originalText.isBlank()) return originalText;

        String plain = TextFormatUtils.stripFormatting(originalText);
        if (plain == null || !TextFilter.shouldTranslate(plain)) return originalText;

        if (!TextFormatUtils.hasFormattingTokens(originalText) && plain.equals(originalText.strip())) {
            String translated = getTranslationBlockingInternal(plain, sourceLang, targetLang, respectOutputGuard);
            return TextFormatUtils.preserveEdgeWhitespace(originalText, translated);
        }

        TextFormatUtils.TranslatableText prepared = TextFormatUtils.prepareForTranslation(originalText);
        if (prepared.skip()) return originalText;

        List<String> translatedFragments = new ArrayList<>();
        boolean anyChanged = false;

        for (String fragment : prepared.getTextsForTranslation()) {
            String cleanFragment = TextFormatUtils.stripFormatting(fragment);
            if (cleanFragment == null || cleanFragment.isBlank()) {
                translatedFragments.add(fragment);
                continue;
            }

            String requestFragment = cleanFragment.strip();
            String translated = getTranslationBlockingInternal(
                requestFragment,
                sourceLang,
                targetLang,
                respectOutputGuard
            );
            translatedFragments.add(translated);
            anyChanged |= !translated.equals(requestFragment);
        }

        return anyChanged ? prepared.reconstruct(translatedFragments) : originalText;
    }

    private String getMultilineTranslationPreservingFormat(String text, String sourceLang, String targetLang) {
        if (text == null || text.isBlank()) return null;

        StringBuilder rebuilt = new StringBuilder(text.length() + 32);
        boolean allReady = true;
        boolean anyChanged = false;
        int lineStart = 0;

        for (int i = 0; i <= text.length(); i++) {
            boolean atEnd = i == text.length();
            boolean atBreak = !atEnd && (text.charAt(i) == '\n' || text.charAt(i) == '\r');
            if (!atEnd && !atBreak) continue;

            String line = text.substring(lineStart, i);
            if (line.isEmpty()) {
                rebuilt.append(line);
            } else {
                String translated = getSingleLineTranslationPreservingFormat(line, sourceLang, targetLang);
                if (translated == null || translated.equals(line)) {
                    allReady = false;
                    rebuilt.append(line);
                } else {
                    rebuilt.append(translated);
                    anyChanged = true;
                }
            }

            if (atBreak) {
                char c = text.charAt(i);
                rebuilt.append(c);
                if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                    rebuilt.append('\n');
                    i++;
                }
                lineStart = i + 1;
            }
        }

        if (!allReady || !anyChanged) return null;
        return rebuilt.toString();
    }

    private String getMultilineTranslationPreservingFormatBlocking(String text, String sourceLang, String targetLang) {
        return getMultilineTranslationPreservingFormatBlocking(text, sourceLang, targetLang, true);
    }

    private String getMultilineTranslationPreservingFormatBlocking(
        String text,
        String sourceLang,
        String targetLang,
        boolean respectOutputGuard
    ) {
        if (text == null || text.isBlank()) return text;

        StringBuilder rebuilt = new StringBuilder(text.length() + 32);
        boolean anyChanged = false;
        int lineStart = 0;

        for (int i = 0; i <= text.length(); i++) {
            boolean atEnd = i == text.length();
            boolean atBreak = !atEnd && (text.charAt(i) == '\n' || text.charAt(i) == '\r');
            if (!atEnd && !atBreak) continue;

            String line = text.substring(lineStart, i);
            String translated = line.isEmpty()
                ? line
                : getSingleLineTranslationPreservingFormatBlocking(
                    line,
                    sourceLang,
                    targetLang,
                    respectOutputGuard
                );
            rebuilt.append(translated);
            anyChanged |= !translated.equals(line);

            if (atBreak) {
                char c = text.charAt(i);
                rebuilt.append(c);
                if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                    rebuilt.append('\n');
                    i++;
                }
                lineStart = i + 1;
            }
        }

        return anyChanged ? rebuilt.toString() : text;
    }

    private static boolean hasLineBreak(String text) {
        return text != null && (text.indexOf('\n') >= 0 || text.indexOf('\r') >= 0);
    }


    private String lookupUniversalBeforeAnything(String text, String sourceLang, String targetLang) {
        if (text == null || text.isBlank()) return null;
        String src = sourceLang == null || sourceLang.isBlank()
            ? ProjectBabelCommon.config().getSourceLang()
            : sourceLang;
        String tgt = targetLang == null || targetLang.isBlank()
            ? LanguageDetector.getTargetLanguageForApi()
            : targetLang;
        String universalResult = UniversalTermsDictionary.getInstance().lookupExact(text, src, tgt);
        if (universalResult != null) {
            cache.put(text, src, tgt, universalResult);
            return universalResult;
        }
        return null;
    }

    public String getTranslation(String strippedText) {
        return getTranslation(
            strippedText,
            ProjectBabelCommon.config().getSourceLang(),
            LanguageDetector.getTargetLanguageForApi()
        );
    }

    public String getTranslation(String strippedText, String sourceLang, String targetLang) {
        if (!ProjectBabelCommon.config().isEnabled() || shuttingDown.get()) return null;
        if (!TextFilter.isScreenFilterBypassed()) {
            if (TextFilter.isDebugScreenOpen()) return null;
            if (TextFilter.isMenuScreen()) return null;
        }
        if (!LanguageDetector.shouldModBeActive()) return null;

        String src = sourceLang == null || sourceLang.isBlank()
            ? ProjectBabelCommon.config().getSourceLang()
            : sourceLang;
        String tgt = targetLang == null || targetLang.isBlank()
            ? LanguageDetector.getTargetLanguageForApi()
            : targetLang;

        // 1. Glossario universal deve vencer cache/API antigas.
        String universalFirst = lookupUniversalBeforeAnything(strippedText, src, tgt);
        if (universalFirst != null) return universalFirst;

        // 2. Lookup exato no dicionário aprendido (sem API).
        // Ele precisa vir antes do cache para correções manuais/glossário aprendido
        // vencerem uma tradução antiga já persistida.
        String dictResult = TranslationDictionary.getInstance().lookupExact(strippedText);
        if (dictResult != null) {
            cache.put(strippedText, src, tgt, dictResult);
            return dictResult;
        }

        // 3. Cache hit
        String cached = cache.get(strippedText, src, tgt);
        if (cached != null) return cached;
        cached = cache.getAnySource(strippedText, tgt);
        if (cached != null) return cached;

        // 4. Anti-loop: texto já é output de uma tradução anterior
        if (cache.isAlreadyTranslated(strippedText)) return null;

        String key = src + "|" + tgt + "|" + strippedText;

        // 4. Em cooldown por falha recente? Não re-enfileira até o TTL expirar
        Long failExpiry = failedKeys.get(key);
        if (failExpiry != null) {
            if (System.currentTimeMillis() < failExpiry) return null; // ainda em cooldown
            failedKeys.remove(key); // TTL expirou — pode tentar de novo
        }

        // 5. Já em processamento?
        if (!pendingKeys.add(key)) return null;

        // 6. Enfileira
        taskQueue.offer(new TranslationTask(strippedText, src, tgt, key,
            TextFilter.estimatePriority(strippedText)));
        return null;
    }

    public String getTranslationBlocking(String strippedText, String sourceLang, String targetLang) {
        return getTranslationBlockingInternal(strippedText, sourceLang, targetLang, true);
    }

    private String getTranslationBlockingInternal(
        String strippedText,
        String sourceLang,
        String targetLang,
        boolean respectOutputGuard
    ) {
        if (!ProjectBabelCommon.config().isEnabled() || shuttingDown.get()) return strippedText;
        if (!TextFilter.isScreenFilterBypassed()) {
            if (TextFilter.isDebugScreenOpen()) return strippedText;
            if (TextFilter.isMenuScreen()) return strippedText;
        }
        if (!LanguageDetector.shouldModBeActive()) return strippedText;

        String text = strippedText == null ? "" : strippedText;
        if (text.isBlank()) return strippedText;

        String src = sourceLang == null || sourceLang.isBlank()
            ? ProjectBabelCommon.config().getSourceLang()
            : sourceLang;
        String tgt = targetLang == null || targetLang.isBlank()
            ? LanguageDetector.getTargetLanguageForApi()
            : targetLang;

        String universalFirst = lookupUniversalBeforeAnything(text, src, tgt);
        if (universalFirst != null) return universalFirst;

        String dictResult = TranslationDictionary.getInstance().lookupExact(text);
        if (dictResult != null) {
            cache.put(text, src, tgt, dictResult);
            return dictResult;
        }

        String cached = cache.get(text, src, tgt);
        if (cached != null) return cached;
        cached = cache.getAnySource(text, tgt);
        if (cached != null) return cached;

        if (respectOutputGuard && cache.isAlreadyTranslated(text)) return text;

        String detectedLanguage = LanguageDetector.detectLanguage(TextFormatUtils.stripFormatting(text));
        if (tgt.equals(detectedLanguage)) return text;
        if (!detectedLanguage.equals("unknown")) src = detectedLanguage;

        try {
            TranslationExecutors.acquireTranslationSlot();
            try {
                String result = selectEngine(text, src, tgt)
                    .get(ProjectBabelCommon.config().getRequestTimeoutMs() + 3000L, TimeUnit.MILLISECONDS);
                result = TextFormatUtils.postProcess(result);
                result = TextFormatUtils.collapseExactDuplicateTranslation(text, result);
                result = TextFormatUtils.collapseRepeatedTranslation(result);
                result = TextFormatUtils.preserveTrailingRomanNumeral(text, result);
                if (result == null || result.isBlank() || result.equalsIgnoreCase(text)) return text;
                cache.put(text, src, tgt, result);
                return result;
            } finally {
                TranslationExecutors.releaseTranslationSlot();
            }
        } catch (Exception e) {
            ProjectBabelCommon.LOGGER.debug("[projectbabel] Falha sincrona '{}': {}", text, e.getMessage());
            return text;
        }
    }

    private void workerLoop() {
        while (!shuttingDown.get() && !Thread.currentThread().isInterrupted()) {
            try {
                TranslationTask task = taskQueue.poll(100, TimeUnit.MILLISECONDS);
                if (task == null) continue;
                TranslationExecutors.acquireTranslationSlot();
                try { executeTask(task); }
                finally { TranslationExecutors.releaseTranslationSlot(); }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); break;
            } catch (Exception e) {
                ProjectBabelCommon.LOGGER.debug("[projectbabel] Worker: {}", e.getMessage());
            }
        }
    }


    private void executeTask(TranslationTask task) {
        try {
            // Checagem de duplicata (outro worker pode ter traduzido enquanto aguardava)
            if (cache.contains(task.text, task.src, task.tgt)) return;
            if (cache.getAnySource(task.text, task.tgt) != null) return;

            String universalResult = UniversalTermsDictionary.getInstance().lookupExact(task.text, task.src, task.tgt);
            if (universalResult != null) {
                cache.put(task.text, task.src, task.tgt, universalResult);
                return;
            }

            String dictResult = TranslationDictionary.getInstance().lookupExact(task.text);
            if (dictResult != null) {
                cache.put(task.text, task.src, task.tgt, dictResult);
                return;
            }

            if (cache.isAlreadyTranslated(task.text)) return;

            String hint = TranslationDictionary.getInstance().buildContextHint(task.text);
            if (hint != null) {
                ProjectBabelCommon.LOGGER.debug("[projectbabel] Hint '{}': {}", task.text, hint);
            }

            String result = selectEngine(task.text, task.src, task.tgt)
                .get(ProjectBabelCommon.config().getRequestTimeoutMs() + 3000L, TimeUnit.MILLISECONDS);

            if (result != null && !result.isBlank()) {
                // Sanitiza antes de comparar e salvar
                result = TextFormatUtils.postProcess(result);
                result = TextFormatUtils.collapseExactDuplicateTranslation(task.text, result);
                result = TextFormatUtils.collapseRepeatedTranslation(result);
                result = TextFormatUtils.preserveTrailingRomanNumeral(task.text, result);
                if (result == null || result.equalsIgnoreCase(task.text)) {
                    // Tradução idêntica — marca cooldown longo para não tentar mais
                    failedKeys.put(task.pendingKey, System.currentTimeMillis() + FAILED_KEY_TTL_MS * 6);
                    return;
                }
                cache.put(task.text, task.src, task.tgt, result);
                ProjectBabelCommon.LOGGER.debug("[projectbabel] [p{}] '{}' -> '{}'",
                    task.priority, task.text, result);
            }

        } catch (TimeoutException e) {
            ProjectBabelCommon.LOGGER.debug("[projectbabel] Timeout: {}", task.text);
            failedKeys.put(task.pendingKey, System.currentTimeMillis() + FAILED_KEY_TTL_MS);
        } catch (Exception e) {
            ProjectBabelCommon.LOGGER.debug("[projectbabel] Falha '{}': {}", task.text, e.getMessage());
            // Coloca em cooldown - evita re-tentar imediatamente quando Google e Lingva falham
            failedKeys.put(task.pendingKey, System.currentTimeMillis() + FAILED_KEY_TTL_MS);
        } finally {
            pendingKeys.remove(task.pendingKey);
            // Limpa failedKeys expiradas periodicamente para não vazar memória
            if (failedKeys.size() > 500) {
                long now = System.currentTimeMillis();
                failedKeys.entrySet().removeIf(e -> e.getValue() < now);
            }
        }
    }

    public String getCachedTranslation(String text, String sourceLang, String targetLang) {
        return cache.get(text, sourceLang, targetLang);
    }

    public String getCachedTranslationAnySource(String text, String targetLang) {
        return cache.getAnySource(text, targetLang);
    }

    public boolean isAlreadyTranslatedValue(String text) {
        return cache.isAlreadyTranslated(text);
    }

    // -----------------------------------------------------------------------
    // Engine: Google principal, Lingva como fallback automático
    //
    // Após GOOGLE_FAIL_THRESHOLD falhas consecutivas do Google, o mod usa
    // Lingva até que o Google volte. O retorno ao Google é automático após
    // GOOGLE_RECOVER_MS milissegundos sem uso do fallback.
    // -----------------------------------------------------------------------

    private static final int  GOOGLE_FAIL_THRESHOLD = 5;
    private static final long GOOGLE_RECOVER_MS     = 60_000L; // 1 minuto

    private final AtomicInteger googleConsecFails   = new AtomicInteger(0);
    private volatile long       lingvaActiveSince   = 0L;

    /**
     * Seleciona a engine para uma tarefa de tradução.
     * Sempre tenta Google primeiro. Se o Google acumular GOOGLE_FAIL_THRESHOLD
     * falhas consecutivas, muda automaticamente para Lingva.
     * Retorna ao Google após GOOGLE_RECOVER_MS sem novas falhas.
     */
    private CompletableFuture<String> selectEngine(String text, String src, String tgt) {
        // Verifica se já passou tempo suficiente para tentar recuperar o Google
        if (lingvaActiveSince > 0
                && System.currentTimeMillis() - lingvaActiveSince > GOOGLE_RECOVER_MS) {
            googleConsecFails.set(0);
            lingvaActiveSince = 0L;
            ProjectBabelCommon.LOGGER.info("[projectbabel] Retornando ao Google após cooldown.");
        }

        boolean usingFallback = googleConsecFails.get() >= GOOGLE_FAIL_THRESHOLD;

        if (usingFallback) {
            return lingva.translate(text, src, tgt);
        }

        return google.translate(text, src, tgt)
            .orTimeout(5, TimeUnit.SECONDS)
            .handle((result, ex) -> {
                if (ex != null || result == null || result.isBlank()) {
                    int fails = googleConsecFails.incrementAndGet();
                    if (fails == GOOGLE_FAIL_THRESHOLD) {
                        lingvaActiveSince = System.currentTimeMillis();
                        ProjectBabelCommon.LOGGER.warn(
                            "[projectbabel] Google falhou {} vezes seguidas — usando Lingva como fallback.", fails);
                    }
                    return null;
                }
                // Google respondeu com sucesso — zera o contador
                googleConsecFails.set(0);
                lingvaActiveSince = 0L;
                return result;
            })
            .thenCompose(result -> {
                if (result != null) return CompletableFuture.completedFuture(result);
                // Google falhou nesta chamada — tenta Lingva imediatamente
                return lingva.translate(text, src, tgt);
            });
    }

    /** Reseta o estado de fallback manualmente (ex: botão no menu). */
    public void resetEngineFallback() {
        googleConsecFails.set(0);
        lingvaActiveSince = 0L;
        google.resetFailures();
        failedKeys.clear();
        ProjectBabelCommon.LOGGER.info("[projectbabel] Fallback resetado — Google voltará a ser usado.");
    }

    public String getActiveEngineName() {
        boolean fallback = googleConsecFails.get() >= GOOGLE_FAIL_THRESHOLD;
        return fallback ? "Lingva (fallback)" : "Google";
    }

    public boolean isUsingFallback() {
        return googleConsecFails.get() >= GOOGLE_FAIL_THRESHOLD;
    }

    public void resetRuntimeStateForWorldJoin() {
        failedKeys.clear();
        pendingKeys.clear();
        taskQueue.clear();
    }

    public TranslationCache getCache(){ return cache; }
    public int getPendingCount()      {
        return pendingKeys.size() + TranslationTriageManager.getInstance().getPendingCount();
    }
    public int getQueuedCount()       { return taskQueue.size(); }
    public int getActiveTranslationCount() { return TranslationExecutors.activeTranslationCount(); }

    public boolean waitForIdle(long timeoutMs) {
        long deadline = System.currentTimeMillis() + Math.max(0L, timeoutMs);
        while (System.currentTimeMillis() < deadline) {
            if (isIdle()) return true;
            try {
                Thread.sleep(250L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return isIdle();
    }

    private boolean isIdle() {
        return pendingKeys.isEmpty()
            && taskQueue.isEmpty()
            && TranslationTriageManager.getInstance().isIdle();
    }

    public void shutdown() {
        if (shuttingDown.compareAndSet(false, true)) {
            cache.saveToDisk();
            TranslationExecutors.shutdown();
            ProjectBabelCommon.LOGGER.info("[projectbabel] Desligado. Scheduler={}", TranslationExecutors.modeSummary());
        }
    }
}
