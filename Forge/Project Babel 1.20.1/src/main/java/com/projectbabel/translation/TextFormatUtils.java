package com.projectbabel.translation;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utilitários para texto de tradução.
 *
 * Estratégia de preservação de formatação:
 *  - códigos §/& e cores hex ficam fora da API de tradução;
 *  - apenas fragmentos de texto real são traduzidos;
 *  - depois os prefixos originais são recolocados nos mesmos pontos;
 *  - placeholders de mods/FTB/Patchouli e JSON components são preservados.
 */
public class TextFormatUtils {

    /**
     * Formatação Minecraft/FTB e placeholders comuns.
     * Exemplos preservados: §a, &6, §#FFAA00, &#FFAA00, {image:...}, {@pagebreak}, {open_url:...}.
     */
    private static final Pattern FORMAT_TOKEN = Pattern.compile(
        "([§&][0-9a-fk-orA-FK-OR]|§#[0-9A-Fa-f]{6}|&#[0-9A-Fa-f]{6}|\\{[A-Za-z0-9_@:\\.-]+[^}]*\\}|\\$\\([^)]*\\)|(?i:\\b(?:shift|ctrl|control|alt|tab|enter|esc|space|backspace|sneak|jump|f\\d{1,2})\\b))"
    );

    /** Compatibilidade com chamadas antigas. */
    private static final Pattern FORMAT_CODE = Pattern.compile(
        "§[0-9a-fk-orA-FK-OR]|§#[0-9A-Fa-f]{6}|&[0-9a-fk-orA-FK-OR]|&#[0-9A-Fa-f]{6}"
    );

    private TextFormatUtils() {}

    public static boolean hasFormattingTokens(String text) {
        return text != null && FORMAT_TOKEN.matcher(text).find();
    }

    public record FormatSegment(String formatPrefix, String textContent) {
        public boolean hasText() {
            return textContent != null
                && !textContent.trim().isEmpty()
                && textContent.chars().anyMatch(Character::isLetter);
        }
    }

    public static List<FormatSegment> parseSegments(String text) {
        List<FormatSegment> segments = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return segments;
        }

        Matcher matcher = FORMAT_TOKEN.matcher(text);
        List<int[]> tokenPositions = new ArrayList<>();
        while (matcher.find()) {
            tokenPositions.add(new int[] { matcher.start(), matcher.end() });
        }

        if (tokenPositions.isEmpty()) {
            segments.add(new FormatSegment("", text));
            return segments;
        }

        int pos = 0;
        StringBuilder currentPrefix = new StringBuilder();
        int i = 0;

        while (i < tokenPositions.size()) {
            int tokenStart = tokenPositions.get(i)[0];
            int tokenEnd = tokenPositions.get(i)[1];

            if (tokenStart > pos && currentPrefix.isEmpty()) {
                String plainBefore = text.substring(pos, tokenStart);
                if (!plainBefore.isEmpty()) {
                    segments.add(new FormatSegment("", plainBefore));
                }
            } else if (tokenStart > pos) {
                String content = text.substring(pos, tokenStart);
                segments.add(new FormatSegment(currentPrefix.toString(), content));
                currentPrefix = new StringBuilder();
            }

            currentPrefix.append(text, tokenStart, tokenEnd);
            pos = tokenEnd;
            i++;

            if (i < tokenPositions.size()) {
                int nextTokenStart = tokenPositions.get(i)[0];
                if (nextTokenStart > pos) {
                    String content = text.substring(pos, nextTokenStart);
                    segments.add(new FormatSegment(currentPrefix.toString(), content));
                    currentPrefix = new StringBuilder();
                    pos = nextTokenStart;
                }
            }
        }

        if (pos < text.length()) {
            segments.add(new FormatSegment(currentPrefix.toString(), text.substring(pos)));
        } else if (!currentPrefix.isEmpty()) {
            segments.add(new FormatSegment(currentPrefix.toString(), ""));
        }

        return segments;
    }

    public static boolean shouldSkipLine(String line) {
        if (line == null) return true;
        String trimmed = line.trim();
        if (trimmed.isEmpty()) return true;
        if (trimmed.equals("{@pagebreak}")) return true;
        if (isJsonComponent(trimmed)) return true;
        String stripped = stripFormatting(trimmed);
        return stripped.trim().isEmpty();
    }

    private static boolean isJsonComponent(String text) {
        if (text == null) return false;
        String trimmed = text.trim();
        if (trimmed.length() < 2) return false;

        boolean objectLike = trimmed.startsWith("{") && trimmed.endsWith("}") && trimmed.contains(":");
        boolean arrayLike = trimmed.startsWith("[") && trimmed.endsWith("]")
            && (trimmed.contains("{") || trimmed.contains("\"") || trimmed.contains(":"));
        return objectLike || arrayLike;
    }

    public static TranslatableText prepareForTranslation(String rawText) {
        if (shouldSkipLine(rawText)) {
            return new TranslatableText(rawText, List.of(), true);
        }
        List<FormatSegment> segments = parseSegments(rawText);
        boolean hasText = segments.stream().anyMatch(FormatSegment::hasText);
        return new TranslatableText(rawText, segments, !hasText);
    }

    public record TranslatableText(String original, List<FormatSegment> segments, boolean skip) {
        public List<String> getTextsForTranslation() {
            if (skip) return List.of();
            List<String> texts = new ArrayList<>();
            for (FormatSegment segment : segments) {
                if (segment.hasText()) {
                    texts.add(segment.textContent());
                }
            }
            return texts;
        }

        public String reconstruct(List<String> translatedTexts) {
            if (skip || translatedTexts == null || translatedTexts.isEmpty()) return original;

            StringBuilder rebuilt = new StringBuilder();
            int translatedIndex = 0;

            for (FormatSegment segment : segments) {
                String prefix = segment.formatPrefix() == null ? "" : segment.formatPrefix();
                String content = segment.textContent() == null ? "" : segment.textContent();

                rebuilt.append(prefix);

                if (segment.hasText()) {
                    String fragment = translatedIndex < translatedTexts.size()
                        ? translatedTexts.get(translatedIndex++)
                        : content;
                    fragment = postProcess(fragment);
                    fragment = preserveEdgeWhitespace(content, fragment);
                    rebuilt.append(fragment);
                } else {
                    rebuilt.append(content);
                }
            }

            String finalText = sanitizeFormatCodes(decodeHtmlEntities(rebuilt.toString()));
            finalText = finalText.replaceAll("\\{\\{m\\d+\\}\\}", "");
            finalText = finalText.replaceAll("\\{\\{g\\d+\\}\\}", "");
            return finalText;
        }
    }

    public static String preserveEdgeWhitespace(String original, String translated) {
        if (translated == null) return original;
        if (original == null || original.isEmpty()) return translated;

        String leading = getLeadingWhitespace(original);
        String trailing = getTrailingWhitespace(original);
        String result = translated;

        if (!leading.isEmpty() && !result.startsWith(leading)) {
            result = leading + result.stripLeading();
        }
        if (!trailing.isEmpty() && !result.endsWith(trailing)) {
            result = result.stripTrailing() + trailing;
        }
        return result;
    }

    public static String preserveTrailingRomanNumeral(String original, String translated) {
        if (original == null || translated == null || original.isBlank() || translated.isBlank()) {
            return translated;
        }

        String originalRoman = trailingRomanToken(original);
        if (originalRoman == null) return translated;

        if (isStandaloneRomanNumeral(original)) {
            return preserveEdgeWhitespace(original, originalRoman);
        }

        String translatedRoman = trailingRomanToken(translated);
        if (originalRoman.equals(translatedRoman)) return translated;

        if (translatedRoman != null) {
            int tokenStart = translated.length() - translatedRoman.length();
            return translated.substring(0, tokenStart) + originalRoman;
        }

        return translated.stripTrailing() + " " + originalRoman;
    }

    public static String collapseExactDuplicateTranslation(String original, String translated) {
        if (original == null || translated == null || original.isBlank() || translated.isBlank()) {
            return translated;
        }

        String originalTrimmed = original.strip();
        String translatedTrimmed = translated.strip();
        if (originalTrimmed.isEmpty()) return translated;

        String duplicated = originalTrimmed + originalTrimmed;
        if (!translatedTrimmed.equalsIgnoreCase(duplicated)) return translated;

        return preserveEdgeWhitespace(translated, originalTrimmed);
    }

    public static String collapseRepeatedTranslation(String translated) {
        if (translated == null || translated.isBlank()) return translated;

        String leading = getLeadingWhitespace(translated);
        String trailing = getTrailingWhitespace(translated);
        String trimmed = translated.strip();
        String collapsed = collapseDuplicateTrailingRoman(collapseRepeatedBody(trimmed));
        if (collapsed.equals(trimmed)) return translated;
        return leading + collapsed + trailing;
    }

    public static String collapseDuplicateTrailingRoman(String text) {
        if (text == null || text.isBlank()) return text;

        int secondEnd = trimEnd(text);
        int secondStart = previousTokenStart(text, secondEnd);
        if (secondStart < 0) return text;

        String second = text.substring(secondStart, secondEnd).toUpperCase(java.util.Locale.ROOT);
        if (!isLikelyRomanNumeral(second)) return text;

        int firstEnd = trimEnd(text, secondStart);
        int firstStart = previousTokenStart(text, firstEnd);
        if (firstStart < 0) return text;

        String first = text.substring(firstStart, firstEnd).toUpperCase(java.util.Locale.ROOT);
        if (!first.equals(second) || !isLikelyRomanNumeral(first)) return text;

        return text.substring(0, firstEnd) + text.substring(secondEnd);
    }

    private static String collapseRepeatedBody(String text) {
        int length = text.length();
        if (length < 4) return text;

        if ((length & 1) == 0) {
            int middle = length / 2;
            String left = text.substring(0, middle);
            String right = text.substring(middle);
            if (sameMeaningfulHalf(left, right)) return left;
        }

        int withoutMiddle = length - 1;
        if ((withoutMiddle & 1) == 0) {
            int middle = withoutMiddle / 2;
            char separator = text.charAt(middle);
            if (Character.isWhitespace(separator)) {
                String left = text.substring(0, middle);
                String right = text.substring(middle + 1);
                if (sameMeaningfulHalf(left, right)) return left;
            }
        }

        return text;
    }

    private static boolean sameMeaningfulHalf(String left, String right) {
        if (left.isBlank() || right.isBlank()) return false;
        if (!left.equalsIgnoreCase(right)) return false;
        return left.chars().anyMatch(Character::isLetter);
    }

    private static String getLeadingWhitespace(String text) {
        int i = 0;
        while (i < text.length() && Character.isWhitespace(text.charAt(i))) i++;
        return text.substring(0, i);
    }

    private static String getTrailingWhitespace(String text) {
        int i = text.length();
        while (i > 0 && Character.isWhitespace(text.charAt(i - 1))) i--;
        return text.substring(i);
    }

    private static int trimEnd(String text) {
        return trimEnd(text, text.length());
    }

    private static int trimEnd(String text, int end) {
        int i = Math.min(end, text.length());
        while (i > 0 && Character.isWhitespace(text.charAt(i - 1))) i--;
        return i;
    }

    private static int previousTokenStart(String text, int end) {
        if (end <= 0) return -1;
        int start = end;
        while (start > 0 && !Character.isWhitespace(text.charAt(start - 1))) start--;
        return start == end ? -1 : start;
    }

    private static String trailingRomanToken(String text) {
        int end = text.length();
        while (end > 0 && Character.isWhitespace(text.charAt(end - 1))) end--;
        if (end <= 0) return null;

        int start = end;
        while (start > 0 && isRomanChar(text.charAt(start - 1))) start--;
        if (start == end) return null;
        if (start > 0 && !Character.isWhitespace(text.charAt(start - 1))) return null;

        String token = text.substring(start, end).toUpperCase(java.util.Locale.ROOT);
        return isLikelyRomanNumeral(token) ? token : null;
    }

    public static boolean isStandaloneRomanNumeral(String text) {
        if (text == null || text.isBlank()) return false;
        String stripped = stripFormatting(text).trim().toUpperCase(java.util.Locale.ROOT);
        return isLikelyRomanNumeral(stripped);
    }

    private static boolean isRomanChar(char c) {
        return c == 'I' || c == 'V' || c == 'X' || c == 'L' || c == 'C' || c == 'D' || c == 'M'
            || c == 'i' || c == 'v' || c == 'x' || c == 'l' || c == 'c' || c == 'd' || c == 'm';
    }

    private static boolean isLikelyRomanNumeral(String token) {
        if (token == null || token.isEmpty() || token.length() > 8) return false;
        return token.matches("M{0,4}(CM|CD|D?C{0,3})(XC|XL|L?X{0,3})(IX|IV|V?I{0,3})");
    }

    /**
     * Pós-processa o resultado de uma tradução:
     *  1. decodifica HTML entities (&amp; &quot; etc.);
     *  2. remove prefixos de formatação inválidos introduzidos pela API;
     *  3. normaliza excesso de espaços internos.
     */
    public static String postProcess(String translated) {
        if (translated == null) return null;
        translated = decodeHtmlEntities(translated);
        translated = sanitizeFormatCodes(translated);
        translated = translated.replace('\u00A0', ' ');
        translated = translated.replaceAll("[ \\t\\x0B\\f]{2,}", " ");
        return collapseRepeatedTranslation(translated);
    }

    public static String decodeHtmlEntities(String text) {
        if (text == null || !text.contains("&")) return text;
        text = text.replace("&quot;",  "\"");
        text = text.replace("&apos;",  "'");
        text = text.replace("&#39;",   "'");
        text = text.replace("&lt;",    "<");
        text = text.replace("&gt;",    ">");
        text = text.replace("&nbsp;",  " ");
        text = text.replace("&#x27;",  "'");
        text = text.replace("&#x2F;",  "/");
        text = text.replace("&amp;",   "&");
        return text;
    }

    public static String sanitizeFormatCodes(String text) {
        if (text == null || (text.indexOf('&') < 0 && text.indexOf('§') < 0)) return text;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if ((c == '&' || c == '§') && i + 1 < text.length()) {
                char next = text.charAt(i + 1);
                if (isValidFormatChar(next)) {
                    sb.append(c);
                } else if (next == '#' && i + 8 <= text.length()) {
                    String hex = text.substring(i + 2, i + 8);
                    if (hex.matches("[0-9A-Fa-f]{6}")) {
                        sb.append(c);
                    }
                } else {
                    // remove só o prefixo inválido; mantém o caractere seguinte
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static boolean isValidFormatChar(char c) {
        return (c >= '0' && c <= '9') ||
               (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F') ||
               (c >= 'k' && c <= 'o') || (c >= 'K' && c <= 'O') ||
               c == 'r' || c == 'R';
    }

    public static String stripFormatting(String text) {
        if (text == null) return null;
        return FORMAT_TOKEN.matcher(FORMAT_CODE.matcher(text).replaceAll("")).replaceAll("").strip();
    }
}
