package com.projectbabel.translation;

import com.projectbabel.ProjectBabelMod;
import com.projectbabel.config.AutoTranslateConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.LanguageManager;

import java.util.Locale;
import java.util.Set;

/**
 * Tracks the current Minecraft language and provides a lightweight detector
 * for the pre-API redundancy filter.
 */
public class LanguageDetector {

    private static final Set<String> PORTUGUESE_CODES = Set.of(
        "pt_br", "pt_pt", "pt"
    );

    private LanguageDetector() {}

    public static boolean shouldModBeActive() {
        return true;
    }

    public static String getTargetLanguageForApi() {
        if (AutoTranslateConfig.isFollowClientLanguage()) {
            return normalizeLanguage(getClientLanguageCode());
        }
        return normalizeLanguage(AutoTranslateConfig.getTargetLang());
    }

    public static String getClientLanguageCode() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.options != null && mc.options.languageCode != null) {
                return mc.options.languageCode.toLowerCase(Locale.ROOT).replace('-', '_');
            }
        } catch (Exception ignored) {}
        return AutoTranslateConfig.getTargetLang().toLowerCase(Locale.ROOT).replace('-', '_');
    }

    public static String normalizeLanguage(String languageCode) {
        if (languageCode == null || languageCode.isBlank()) return "en";

        String normalized = languageCode.toLowerCase(Locale.ROOT).replace('-', '_');
        int separator = normalized.indexOf('_');
        return separator > 0 ? normalized.substring(0, separator) : normalized;
    }

    public static String detectLanguage(String text) {
        if (text == null || text.isBlank()) return "unknown";

        int latinLetters = 0;
        int cyrillic = 0;
        int cjk = 0;
        int kana = 0;
        int portugueseMarks = 0;
        int spanishMarks = 0;
        int frenchMarks = 0;

        for (int i = 0; i < text.length(); i++) {
            char c = Character.toLowerCase(text.charAt(i));
            if (c >= 'a' && c <= 'z') latinLetters++;
            else if (c >= '\u0400' && c <= '\u04FF') cyrillic++;
            else if (c >= '\u4E00' && c <= '\u9FFF') cjk++;
            else if (c >= '\u3040' && c <= '\u30FF') kana++;

            switch (c) {
                case '\u00E3', '\u00F5', '\u00E7' -> portugueseMarks += 2;
                case '\u00E1', '\u00E0', '\u00E2', '\u00E9', '\u00EA',
                     '\u00ED', '\u00F3', '\u00F4', '\u00FA' -> portugueseMarks++;
                case '\u00F1', '\u00BF', '\u00A1' -> spanishMarks += 2;
                case '\u00E8', '\u00EB', '\u00EE', '\u00EF', '\u00F9',
                     '\u00FB', '\u0153' -> frenchMarks += 2;
                default -> {}
            }
        }

        if (kana > 0) return "ja";
        if (cjk > 0) return "zh";
        if (cyrillic > 0) return "ru";
        if (portugueseMarks >= 2) return "pt";
        if (spanishMarks >= 2) return "es";
        if (frenchMarks >= 2) return "fr";

        String lower = ' ' + text.toLowerCase(Locale.ROOT) + ' ';
        int ptScore = scoreWords(lower,
            " de ", " da ", " do ", " das ", " dos ", " uma ", " um ",
            " ao ", " aos ", " esta ", " voce ", " você ", " para ", " com ",
            " aperte ", " pressione ", " segure ", " ver ", " mais ",
            " detalhes ", " descrição ", " descricao ", " encantamento ",
            " dano ", " ataque ", " item ", " bloco ", " missão ", " missao ");
        int esScore = scoreWords(lower,
            " el ", " la ", " los ", " las ", " una ", " un ", " con ",
            " para ", " esta ", " este ", " que ", " pulsa ", " presiona ",
            " mantener ", " detalles ", " daño ");
        int enScore = scoreWords(lower,
            " the ", " and ", " with ", " for ", " from ", " into ", " when ",
            " press ", " hold ", " shift ", " ctrl ", " more ", " details ",
            " enchantment ", " damage ", " attack ");

        if (ptScore >= 2 && ptScore > esScore) return "pt";
        if (esScore >= 2 && esScore > ptScore) return "es";
        if (enScore >= 1) return "en";

        return latinLetters > 0 ? "en" : "unknown";
    }

    private static int scoreWords(String text, String... needles) {
        int score = 0;
        for (String needle : needles) {
            if (text.contains(needle)) score++;
        }
        return score;
    }

    public static void prepopulateFromGameLanguages() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null) return;

            LanguageManager lm = mc.getLanguageManager();
            if (lm == null) return;

            net.minecraft.locale.Language currentLang = net.minecraft.locale.Language.getInstance();
            if (currentLang == null) return;

            String currentCode = lm.getSelected();
            if (currentCode == null) return;

            if (PORTUGUESE_CODES.contains(currentCode.toLowerCase(Locale.ROOT))) {
                ProjectBabelMod.LOGGER.info(
                    "[projectbabel] Jogo em {} - mod ativo com alvo {}.",
                    currentCode,
                    getTargetLanguageForApi()
                );
                return;
            }

            ProjectBabelMod.LOGGER.info(
                "[projectbabel] Idioma do jogo: {}. Mod ativo (traduzindo para {}).",
                currentCode,
                getTargetLanguageForApi()
            );
        } catch (Exception e) {
            ProjectBabelMod.LOGGER.debug("[projectbabel] prepopulateFromGameLanguages: {}", e.getMessage());
        }
    }
}
