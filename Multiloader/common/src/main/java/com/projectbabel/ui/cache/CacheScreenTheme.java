package com.projectbabel.ui.cache;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;

/** Shared layout constants, palette and tiny drawing helpers for the cache UI. */
public final class CacheScreenTheme {
    private CacheScreenTheme() {}

    public static final int SIDEBAR_W     = 170;
    public static final int ENTRY_HEIGHT  = 24;
    public static final int HEADER_HEIGHT = 86;
    public static final int PADDING       = 6;
    public static final int SCROLLBAR_W   = 6;

    public static final int C_BG           = 0xD9080A10;
    public static final int C_SIDEBAR_BG   = 0xE00E1118;
    public static final int C_HEADER_TOP   = 0xE0141821;
    public static final int C_HEADER_BOT   = 0xE00B0D13;
    public static final int C_PANEL        = 0xAA151A24;
    public static final int C_BORDER       = 0xFF303746;
    public static final int C_ENTRY_ODD    = 0x8810141C;
    public static final int C_ENTRY_EVEN   = 0x88191E28;
    public static final int C_ENTRY_HOVER  = 0x3347A3FF;
    public static final int C_ENTRY_SELECT = 0x445FF090;
    public static final int C_ORIGINAL     = 0xFFD2D8E3;
    public static final int C_ARROW        = 0xFF617087;
    public static final int C_TRANSLATED   = 0xFF7CF0A2;
    public static final int C_TITLE        = 0xFFE7EDF7;
    public static final int C_STATS        = 0xFF8F9AAB;
    public static final int C_SCROLLBAR    = 0xAA7E8CA6;
    public static final int C_SCROLLBAR_BG = 0x33262B36;
    public static final int C_EMPTY        = 0xFF667085;
    public static final int C_LABEL        = 0xFF9AA6BB;
    public static final int C_ACCENT       = 0xFF5FF090;
    public static final int C_WARN         = 0xFFFFC857;
    public static final int C_BTN_ON       = 0xAA1A6632;
    public static final int C_BTN_OFF      = 0xAA661A22;
    public static final int C_BTN_HUD_ON   = 0xAA1A3366;
    public static final int C_BTN_HUD_OFF  = 0xAA333344;
    public static final int C_ENGINE_OK    = 0xFFBBDD55;
    public static final int C_ENGINE_FB    = 0xFFFFAA33;

    public static int listX() {
        return SIDEBAR_W + PADDING * 2 + 1;
    }

    public static int listY() {
        return HEADER_HEIGHT;
    }

    public static int listW(int screenWidth) {
        return screenWidth - listX() - SCROLLBAR_W - PADDING;
    }

    public static int listH(int screenHeight) {
        return screenHeight - HEADER_HEIGHT;
    }

    public static void drawBoxBorder(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }

    public static void fillButtonBackplate(GuiGraphics g, Button button, int color) {
        if (button == null) return;
        g.fill(
            button.getX() - 1,
            button.getY() - 1,
            button.getX() + button.getWidth() + 1,
            button.getY() + button.getHeight() + 1,
            color
        );
    }

    public static String compactNumber(int value) {
        if (value >= 1_000_000) return value / 1_000_000 + "M";
        if (value >= 10_000) return value / 1_000 + "K";
        return Integer.toString(value);
    }

    public static String truncate(Font font, String text, int maxPx) {
        if (text == null || text.isEmpty()) return "";
        if (maxPx <= 0) return "...";
        if (font.width(text) <= maxPx) return text;
        String t = text;
        while (t.length() > 1 && font.width(t + "...") > maxPx) {
            t = t.substring(0, t.length() - 1);
        }
        return t + "...";
    }

    public static String shortEngine(String engine) {
        if (engine == null || engine.isBlank()) return "Engine";
        int space = engine.indexOf(' ');
        return space > 0 ? engine.substring(0, space) : engine;
    }
}
