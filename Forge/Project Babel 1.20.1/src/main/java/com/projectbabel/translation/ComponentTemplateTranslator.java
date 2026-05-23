package com.projectbabel.translation;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.LiteralContents;

import java.util.ArrayList;
import java.util.List;

/**
 * Rebuilds rich components by translating only the textual glue around styled
 * anchors. This keeps FTB/Patchouli mentions clickable/hoverable while allowing
 * a sentence with many item mentions to be translated as one template.
 */
public final class ComponentTemplateTranslator {

    private static final String TOKEN_PREFIX = "{pb";
    private static final String TOKEN_SUFFIX = "}";
    private static final int MAX_ANCHORS = 16;

    private ComponentTemplateTranslator() {}

    public static boolean isCandidate(Component component) {
        return component != null && !component.getSiblings().isEmpty();
    }

    public static Component translateCacheOnly(Component component) {
        return translate(component, true);
    }

    public static Component translateBlocking(Component component) {
        return translate(component, false);
    }

    private static Component translate(Component component, boolean cacheOnly) {
        if (!isCandidate(component)) return component;

        Template template = buildTemplate(component);
        if (template == null || template.anchors().isEmpty()) return component;
        if (template.text().equals(component.getString())) return component;

        String translated = cacheOnly
            ? TranslationPipeline.translateStringCacheOnly(template.text())
            : TranslationPipeline.translateStringBlocking(template.text());
        if (translated == null || translated.isBlank() || translated.equals(template.text())) return component;

        Component rebuilt = rebuild(component, template, translated, cacheOnly);
        if (rebuilt == null) return component;

        if (!cacheOnly) {
            TranslationTriageManager.getInstance().warmText(template.text(), translated);
            TranslationTriageManager.getInstance().warmComponent(component, rebuilt);
        }
        TranslationSkipRegistry.skip(rebuilt);
        return rebuilt;
    }

    private static Template buildTemplate(Component root) {
        List<Anchor> anchors = new ArrayList<>();
        StringBuilder text = new StringBuilder(Math.max(32, root.getString().length() + 16));
        Style baseStyle = style(root);

        append(root, baseStyle, true, text, anchors);
        if (anchors.isEmpty() || anchors.size() > MAX_ANCHORS) return null;

        return new Template(text.toString(), anchors);
    }

    private static void append(
        Component component,
        Style baseStyle,
        boolean root,
        StringBuilder text,
        List<Anchor> anchors
    ) {
        if (component == null) return;

        if (!root && shouldAnchor(component, baseStyle)) {
            String token = TOKEN_PREFIX + anchors.size() + TOKEN_SUFFIX;
            anchors.add(new Anchor(token, component));
            text.append(token);
            return;
        }

        if (component.getContents() instanceof LiteralContents literal) {
            text.append(literal.text());
        } else if (!root) {
            String token = TOKEN_PREFIX + anchors.size() + TOKEN_SUFFIX;
            anchors.add(new Anchor(token, component));
            text.append(token);
            return;
        }

        for (Component sibling : component.getSiblings()) {
            append(sibling, baseStyle, false, text, anchors);
        }
    }

    private static boolean shouldAnchor(Component component, Style baseStyle) {
        if (!(component.getContents() instanceof LiteralContents) || !component.getSiblings().isEmpty()) {
            return true;
        }

        Style style = style(component);
        return !style.equals(baseStyle) && !style.equals(Style.EMPTY);
    }

    private static Component rebuild(
        Component original,
        Template template,
        String translated,
        boolean cacheOnly
    ) {
        MutableComponent rebuilt = Component.literal("").withStyle(style(original));
        int cursor = 0;

        for (Anchor anchor : template.anchors()) {
            int tokenPos = translated.indexOf(anchor.token(), cursor);
            if (tokenPos < 0) return null;

            appendLiteral(rebuilt, translated.substring(cursor, tokenPos), style(original));
            Component anchorComponent = cacheOnly
                ? TranslationPipeline.translateComponentTreeCacheOnly(anchor.component())
                : TranslationPipeline.translateComponentTree(anchor.component());
            rebuilt.append(anchorComponent == null ? anchor.component() : anchorComponent);
            cursor = tokenPos + anchor.token().length();
        }

        appendLiteral(rebuilt, translated.substring(cursor), style(original));
        return rebuilt;
    }

    private static void appendLiteral(MutableComponent target, String text, Style style) {
        if (text == null || text.isEmpty()) return;
        target.append(Component.literal(text).withStyle(style));
    }

    private static Style style(Component component) {
        Style style = component == null ? null : component.getStyle();
        return style == null ? Style.EMPTY : style;
    }

    private record Template(String text, List<Anchor> anchors) {}

    private record Anchor(String token, Component component) {}
}
