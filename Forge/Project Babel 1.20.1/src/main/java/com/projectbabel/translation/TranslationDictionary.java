package com.projectbabel.translation;

import com.projectbabel.ProjectBabelMod;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Dicionário de tokens: mapeia palavras individuais para suas traduções conhecidas.
 *
 * Objetivo: se "log" já foi traduzido como "tronco", então quando aparecer
 * "Oak Log" → usa "tronco" como pista e envia à engine "Oak tronco" ou faz
 * substituição parcial — resultando em "Tronco de Carvalho" via contexto.
 *
 * Estratégia implementada (mais robusta que substituição direta):
 *  1. Quando "Oak Log" → "Tronco de Carvalho" é salvo, extrai tokens:
 *     "oak"→"carvalho", "log"→"tronco" (heurística por posição/bigramas)
 *  2. Quando novo texto como "Birch Log" aparece, verifica se "log" está no dicionário.
 *     Se sim, passa o contexto para a engine como HINT — não substitui diretamente
 *     (substituição direta quebraria gramática).
 *
 * Também faz lookup direto: se o texto inteiro bate exatamente com uma entrada do
 * dicionário, retorna imediatamente sem chamar a API.
 */
public class TranslationDictionary {

    private static final int    MAX_ENTRIES      = 5000;
    private static final int    MAX_TOKEN_LEN    = 30;
    // Tokens curtos demais causam falsos positivos ("a", "of", "the")
    private static final int    MIN_TOKEN_LEN    = 3;
    private static final Pattern WORD_SPLITTER   = Pattern.compile("[\\s\\-_]+");
    private static final Pattern NON_WORD        = Pattern.compile("[^\\p{L}\\p{N}\\s\\-_]");

    // token em minúsculas → tradução
    private final Map<String, String> tokenMap   = new ConcurrentHashMap<>();
    // texto completo em minúsculas → tradução (lookup exato case-insensitive)
    private final Map<String, String> exactMap   = new ConcurrentHashMap<>();

    private TranslationDictionary() {}

    private static final TranslationDictionary INSTANCE = new TranslationDictionary();
    public static TranslationDictionary getInstance() { return INSTANCE; }

    /**
     * Registra um par de tradução e extrai tokens individuais.
     * Chamado pelo TranslationCache.put() automaticamente.
     */
    public void register(String original, String translated) {
        if (original == null || translated == null) return;
        String origClean = original.trim();
        String transClean = translated.trim();
        if (origClean.isEmpty() || transClean.isEmpty()) return;
        if (origClean.equalsIgnoreCase(transClean)) return; // sem tradução real

        // Lookup exato (case-insensitive)
        if (exactMap.size() < MAX_ENTRIES) {
            exactMap.put(origClean.toLowerCase(Locale.ROOT), transClean);
        }

        // Extrai tokens só para textos multi-palavra (evita ruído de palavras únicas)
        String[] origTokens  = tokenize(origClean);
        String[] transTokens = tokenize(transClean);

        if (origTokens.length < 2 || transTokens.length < 2) return;

        // Heurística: alinha tokens por posição se tiverem o mesmo número
        // Ex: "Oak Log" (2) → "Tronco de Carvalho" (3): não alinha
        // Ex: "Oak Log" (2) → "Tronco Carvalho" (2): oak→tronco, log→carvalho ✓
        if (origTokens.length == transTokens.length) {
            for (int i = 0; i < origTokens.length; i++) {
                String o = origTokens[i].toLowerCase(Locale.ROOT);
                String t = transTokens[i];
                if (o.length() >= MIN_TOKEN_LEN && o.length() <= MAX_TOKEN_LEN
                        && t.length() >= MIN_TOKEN_LEN && tokenMap.size() < MAX_ENTRIES) {
                    tokenMap.putIfAbsent(o, t); // não sobrescreve — primeiro a chegar prevalece
                }
            }
        }
    }

    /**
     * Lookup exato: retorna tradução se o texto inteiro já foi traduzido antes.
     * Case-insensitive. Retorna null se não encontrado.
     */
    public String lookupExact(String text) {
        if (text == null || text.isBlank()) return null;
        return exactMap.get(text.trim().toLowerCase(Locale.ROOT));
    }

    /**
     * Verifica se o texto contém tokens conhecidos que podem guiar a tradução.
     * Retorna mapa de token→tradução para os tokens encontrados.
     * Usado pelo TranslationManager para enriquecer o contexto.
     */
    public Map<String, String> findKnownTokens(String text) {
        if (text == null || text.isBlank() || tokenMap.isEmpty()) return Collections.emptyMap();

        String[] tokens = tokenize(NON_WORD.matcher(text).replaceAll(" "));
        Map<String, String> found = new LinkedHashMap<>();

        for (String t : tokens) {
            String lower = t.toLowerCase(Locale.ROOT);
            if (lower.length() >= MIN_TOKEN_LEN) {
                String trans = tokenMap.get(lower);
                if (trans != null) found.put(t, trans);
            }
        }
        return found;
    }

    /**
     * Constrói um hint de contexto para incluir no prompt da engine.
     * Ex: "Known: log=tronco, oak=carvalho"
     * Retorna null se não há tokens conhecidos.
     */
    public String buildContextHint(String text) {
        Map<String, String> known = findKnownTokens(text);
        if (known.isEmpty()) return null;
        StringBuilder sb = new StringBuilder();
        known.forEach((orig, trans) -> {
            if (sb.length() > 0) sb.append(", ");
            sb.append(orig).append("=").append(trans);
        });
        return sb.toString();
    }


    /** Limpa o dicionário (chamado quando o cache é limpo). */
    public void clear() {
        tokenMap.clear();
        exactMap.clear();
    }

    private String[] tokenize(String text) {
        return WORD_SPLITTER.split(text.trim());
    }
}
