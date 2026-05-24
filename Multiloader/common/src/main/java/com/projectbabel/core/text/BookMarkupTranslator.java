package com.projectbabel.core.text;

import com.projectbabel.api.TranslationContext;
import net.minecraft.ChatFormatting;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.LiteralContents;
import net.minecraft.network.chat.contents.TranslatableContents;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.projectbabel.core.pipeline.TranslationPipeline;
/**
 * Translation helper for book/layout markup.
 *
 * Patchouli text is parsed after macros such as $(bold), $(br), $(l:...), $().
 * Translating the whole raw string lets the translator move/delete those tokens,
 * which breaks the span parser and therefore all formatting.  This helper keeps
 * every command token byte-for-byte and translates only visible text between them.
 */
public final class BookMarkupTranslator {

    private static final Pattern PATCHOULI_COMMAND = Pattern.compile("\\$\\([^)]*\\)");
    private static final Pattern LINE_SEPARATOR = Pattern.compile("\\R");

    private BookMarkupTranslator() {}

    public enum Mode {
        CACHE_ONLY,
        ASYNC,
        BLOCKING
    }

    public static Component translatePatchouliComponentCacheOnly(Component component) {
        return translatePatchouliComponent(component, Mode.CACHE_ONLY);
    }

    public static Component translatePatchouliComponentAsync(Component component) {
        return translatePatchouliComponent(component, Mode.ASYNC);
    }

    public static Component translatePatchouliComponentBlocking(Component component) {
        return translatePatchouliComponent(component, Mode.BLOCKING);
    }

    public static Component translatePatchouliComponent(Component component, Mode mode) {
        if (component == null) return null;

        MutableComponent rebuilt = null;

        if (component.getContents() instanceof LiteralContents literal) {
            String original = literal.text();
            String translated = translatePatchouliString(original, mode);
            if (translated != null && !translated.equals(original)) {
                rebuilt = Component.literal(translated)
                    .withStyle(component.getStyle() == null ? Style.EMPTY : component.getStyle());
            }
        } else if (component.getContents() instanceof TranslatableContents translatable) {
            String localized = resolveTranslatable(translatable);
            if (localized != null && hasTranslatablePatchouliText(localized)) {
                String translated = translatePatchouliString(localized, mode);
                if (translated != null && !translated.equals(localized)) {
                    rebuilt = Component.literal(translated)
                        .withStyle(component.getStyle() == null ? Style.EMPTY : component.getStyle());
                }
            }

            if (rebuilt == null) {
                Object[] args = translatable.getArgs();
                Object[] translatedArgs = args;
                boolean changed = false;

                for (int i = 0; i < args.length; i++) {
                    Object arg = args[i];
                    Object translatedArg = translatePatchouliArgument(arg, mode);
                    if (translatedArg != arg) {
                        if (translatedArgs == args) translatedArgs = args.clone();
                        translatedArgs[i] = translatedArg;
                        changed = true;
                    }
                }

                if (changed) {
                    rebuilt = Component.translatable(translatable.getKey(), translatedArgs)
                        .withStyle(component.getStyle() == null ? Style.EMPTY : component.getStyle());
                }
            }
        }

        for (Component sibling : component.getSiblings()) {
            Component translatedSibling = translatePatchouliComponent(sibling, mode);
            if (translatedSibling != sibling && rebuilt == null) {
                rebuilt = copyBase(component);
            }
            if (rebuilt != null) rebuilt.append(translatedSibling);
        }

        return rebuilt == null ? component : rebuilt;
    }

    private static String resolveTranslatable(TranslatableContents translatable) {
        if (translatable == null) return null;

        String key = translatable.getKey();
        if (key == null || key.isBlank()) return null;

        try {
            if (!I18n.exists(key)) return null;

            Object[] args = translatable.getArgs();
            Object[] localizedArgs = args;
            for (int i = 0; i < args.length; i++) {
                Object arg = args[i];
                Object localizedArg = localizeTranslatableArgument(arg);
                if (localizedArg != arg) {
                    if (localizedArgs == args) localizedArgs = args.clone();
                    localizedArgs[i] = localizedArg;
                }
            }

            String localized = I18n.get(key, localizedArgs);
            if (localized == null || localized.isBlank() || localized.equals(key)) return null;
            return localized;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Object localizeTranslatableArgument(Object arg) {
        if (arg instanceof Component component) return component.getString();
        return arg;
    }

    private static Object translatePatchouliArgument(Object arg, Mode mode) {
        if (arg instanceof Component component) return translatePatchouliComponent(component, mode);
        if (arg instanceof String string) {
            String translated = translatePatchouliString(string, mode);
            return translated != null && !translated.equals(string) ? translated : arg;
        }
        return arg;
    }

    private static MutableComponent copyBase(Component component) {
        MutableComponent base;
        if (component.getContents() instanceof LiteralContents literal) {
            base = Component.literal(literal.text());
        } else if (component.getContents() instanceof TranslatableContents translatable) {
            base = Component.translatable(translatable.getKey(), translatable.getArgs());
        } else {
            // Do not flatten unknown contents with their visible string; that can
            // erase hover/click/image payloads.  Preserve style and only append
            // translated siblings.
            base = Component.empty();
        }
        return base.withStyle(component.getStyle() == null ? Style.EMPTY : component.getStyle());
    }

    public static boolean hasTranslatablePatchouliText(Component component) {
        if (component == null) return false;
        if (component.getContents() instanceof LiteralContents literal
            && hasTranslatablePatchouliText(literal.text())) {
            return true;
        }
        if (component.getContents() instanceof TranslatableContents translatable) {
            String localized = resolveTranslatable(translatable);
            if (localized != null && hasTranslatablePatchouliText(localized)) return true;

            for (Object arg : translatable.getArgs()) {
                if (arg instanceof Component child && hasTranslatablePatchouliText(child)) return true;
                if (arg instanceof String text && hasTranslatablePatchouliText(text)) return true;
            }
        }
        for (Component sibling : component.getSiblings()) {
            if (hasTranslatablePatchouliText(sibling)) return true;
        }
        return false;
    }

    public static boolean hasTranslatablePatchouliText(String text) {
        if (text == null || text.isBlank()) return false;
        Matcher matcher = PATCHOULI_COMMAND.matcher(text);
        int cursor = 0;
        while (matcher.find()) {
            if (containsTranslatablePlainText(text.substring(cursor, matcher.start()))) return true;
            cursor = matcher.end();
        }
        return containsTranslatablePlainText(text.substring(cursor));
    }

    public static void collectPatchouliTextSegments(String text, Set<String> out) {
        if (text == null || out == null) return;
        Matcher matcher = PATCHOULI_COMMAND.matcher(text);
        int cursor = 0;
        while (matcher.find()) {
            collectPlainSegments(text.substring(cursor, matcher.start()), out);
            cursor = matcher.end();
        }
        collectPlainSegments(text.substring(cursor), out);
    }

    private static String translatePatchouliString(String text, Mode mode) {
        if (text == null || text.isEmpty()) return text;

        Matcher matcher = PATCHOULI_COMMAND.matcher(text);
        StringBuilder out = null;
        int cursor = 0;

        while (matcher.find()) {
            String before = text.substring(cursor, matcher.start());
            String translated = translatePlainPreservingLineBreaks(before, mode);
            if (out != null || !before.equals(translated)) {
                if (out == null) out = new StringBuilder(text.length() + 32).append(text, 0, cursor);
                out.append(translated);
            }
            if (out != null) out.append(matcher.group());
            cursor = matcher.end();
        }

        String tail = text.substring(cursor);
        String translatedTail = translatePlainPreservingLineBreaks(tail, mode);
        if (out == null && tail.equals(translatedTail)) return text;
        if (out == null) out = new StringBuilder(text.length() + 32).append(text, 0, cursor);
        out.append(translatedTail);
        return out.toString();
    }

    private static String translatePlainPreservingLineBreaks(String text, Mode mode) {
        if (text == null || text.isEmpty()) return text;

        Matcher matcher = LINE_SEPARATOR.matcher(text);
        StringBuilder out = null;
        int cursor = 0;
        while (matcher.find()) {
            String line = text.substring(cursor, matcher.start());
            String translated = translatePlainSegment(line, mode);
            if (out != null || !line.equals(translated)) {
                if (out == null) out = new StringBuilder(text.length() + 32).append(text, 0, cursor);
                out.append(translated);
            }
            if (out != null) out.append(matcher.group());
            cursor = matcher.end();
        }

        String tail = text.substring(cursor);
        String translatedTail = translatePlainSegment(tail, mode);
        if (out == null && tail.equals(translatedTail)) return text;
        if (out == null) out = new StringBuilder(text.length() + 32).append(text, 0, cursor);
        out.append(translatedTail);
        return out.toString();
    }

    private static String translatePlainSegment(String segment, Mode mode) {
        if (!shouldTranslatePlainSegment(segment)) return segment;

        int start = 0;
        int end = segment.length();
        while (start < end && Character.isWhitespace(segment.charAt(start))) start++;
        while (end > start && Character.isWhitespace(segment.charAt(end - 1))) end--;

        String prefix = segment.substring(0, start);
        String core = segment.substring(start, end);
        String suffix = segment.substring(end);
        if (core.isBlank()) return segment;

        String translated = switch (mode) {
            case CACHE_ONLY -> TranslationPipeline.translateString(core, TranslationContext.book(false).asCacheOnly());
            case ASYNC -> TranslationPipeline.translateString(core, TranslationContext.book(false));
            case BLOCKING -> TranslationPipeline.translateString(core, TranslationContext.book(true));
        };

        if (translated == null || translated.equals(core)) return segment;
        return prefix + translated + suffix;
    }

    private static boolean containsTranslatablePlainText(String segment) {
        if (segment == null || segment.isEmpty()) return false;
        Matcher matcher = LINE_SEPARATOR.matcher(segment);
        int cursor = 0;
        while (matcher.find()) {
            if (shouldTranslatePlainSegment(segment.substring(cursor, matcher.start()))) return true;
            cursor = matcher.end();
        }
        return shouldTranslatePlainSegment(segment.substring(cursor));
    }

    private static void collectPlainSegments(String segment, Set<String> out) {
        if (segment == null || segment.isEmpty()) return;
        Matcher matcher = LINE_SEPARATOR.matcher(segment);
        int cursor = 0;
        while (matcher.find()) {
            addPlainSegment(segment.substring(cursor, matcher.start()), out);
            cursor = matcher.end();
        }
        addPlainSegment(segment.substring(cursor), out);
    }

    private static void addPlainSegment(String segment, Set<String> out) {
        if (!shouldTranslatePlainSegment(segment)) return;
        String trimmed = segment.strip();
        if (!trimmed.isBlank()) out.add(trimmed);
    }

    private static boolean shouldTranslatePlainSegment(String text) {
        if (text == null) return false;
        String plain = TextFormatUtils.stripFormatting(text);
        if (plain == null) return false;
        plain = plain.strip();
        if (plain.length() < 3) return false;
        if (!TextFilter.shouldTranslate(plain)) return false;

        if (plain.startsWith("$(") || plain.startsWith("#") || plain.startsWith("@")) return false;
        if (plain.startsWith("http://") || plain.startsWith("https://")) return false;
        if (plain.startsWith("patchouli.") || plain.startsWith("item.") || plain.startsWith("block.")) return false;
        if (plain.indexOf(' ') < 0 && (plain.indexOf(':') >= 0 || plain.indexOf('/') >= 0 || plain.indexOf('\\') >= 0)) return false;
        if (plain.matches("^[a-z0-9_.:-]+$") && (plain.contains(".") || plain.contains("_") || plain.contains(":"))) return false;

        // Formatting reset codes converted by some packs should remain untouched.
        for (ChatFormatting formatting : ChatFormatting.values()) {
            String name = formatting.getName();
            if (name != null && plain.equalsIgnoreCase(name)) return false;
        }

        return true;
    }
}
