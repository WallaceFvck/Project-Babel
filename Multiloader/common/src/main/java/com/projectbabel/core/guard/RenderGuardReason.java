package com.projectbabel.core.guard;

/**
 * Reasons why the generic Font/GuiGraphics hooks must not translate text.
 *
 * Keeping the reason explicit avoids using one global boolean for unrelated
 * cases such as Project Babel's own UI and third-party book renderers that
 * already performed layout before drawing.
 */
public enum RenderGuardReason {
    /** Project Babel is rendering its own overlay/cache UI. */
    INTERNAL_UI,

    /** A guide/book renderer already measured or wrapped text before draw. */
    PRELAID_OUT_CONTENT,

    /** A mod integration already translated/wrapped the visible content. */
    PRETRANSLATED_MOD_CONTENT,

    /** Compatibility fallback for old call sites that only call enter(). */
    LEGACY
}
