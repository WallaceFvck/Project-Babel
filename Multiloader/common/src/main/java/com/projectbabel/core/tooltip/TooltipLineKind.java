package com.projectbabel.core.tooltip;

/**
 * Classification used by the tooltip translation controller.
 *
 * Tooltips are rendered by more than one Forge/Minecraft hook. Keeping the line
 * decision in a single enum prevents each mixin from re-implementing slightly
 * different rules for titles, enchantment descriptions and already translated
 * lines.
 */
public enum TooltipLineKind {
    NULL,
    ITEM_TITLE,
    EMPTY,
    ALREADY_HANDLED,
    ENCHANTMENT_DESCRIPTION,
    GENERIC_TEXT
}
