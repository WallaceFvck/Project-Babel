package com.projectbabel.overlay;

import com.projectbabel.config.AutoTranslateConfig;
import com.projectbabel.translation.RenderingGuard;
import com.projectbabel.translation.TranslationCache;
import com.projectbabel.translation.TranslationManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Compact HUD for Project Babel.
 * RenderingGuard prevents these labels from entering the translation pipeline.
 */
public class TranslationOverlay {

    private static final long OFF_DISPLAY_MS = 3000L;
    private static final int MARGIN = 8;
    private static final int PADDING_X = 7;
    private static final int PADDING_Y = 5;
    private static final int LINE_H = 10;
    private static final int DOT = 5;

    private static final int BG_TOP = 0xDD10131A;
    private static final int BG_BOTTOM = 0xDD171A22;
    private static final int BORDER = 0xAA3D4657;
    private static final int BAR_BG = 0x66262B36;

    private static final int COLOR_TITLE = 0xFFE7EDF7;
    private static final int COLOR_MUTED = 0xFF9AA4B3;
    private static final int COLOR_ENABLED = 0xFF5FF090;
    private static final int COLOR_DISABLED = 0xFFFF6B6B;
    private static final int COLOR_BUSY = 0xFFFFC857;
    private static final int COLOR_FALLBACK = 0xFF7DB7FF;

    private static long lastStateChangeMs = 0L;
    private static boolean lastEnabledState = true;

    private TranslationOverlay() {}

    public static void render(GuiGraphics graphics) {
        if (!AutoTranslateConfig.isShowHudIndicator()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;

        boolean enabled = AutoTranslateConfig.isEnabled();
        if (enabled != lastEnabledState) {
            lastEnabledState = enabled;
            lastStateChangeMs = System.currentTimeMillis();
        }

        if (!enabled && System.currentTimeMillis() - lastStateChangeMs > OFF_DISPLAY_MS) {
            return;
        }

        RenderingGuard.enter();
        try {
            renderInternal(graphics, mc, enabled);
        } finally {
            RenderingGuard.exit();
        }
    }

    private static void renderInternal(GuiGraphics graphics, Minecraft mc, boolean enabled) {
        TranslationManager manager = TranslationManager.getInstance();
        TranslationCache cache = manager.getCache();

        int pending = manager.getPendingCount();
        int queued = manager.getQueuedCount();
        int active = manager.getActiveTranslationCount();
        boolean fallback = manager.isUsingFallback();
        boolean busy = enabled && (pending > 0 || queued > 0 || active > 0);
        boolean debug = mc.options.renderDebug;

        Font font = mc.font;
        String title = "Project Babel";
        String engine = shortEngine(manager.getActiveEngineName());
        String state = !enabled ? "OFF" : busy ? "BUSY" : "READY";
        String firstLine = state + "  " + engine + (AutoTranslateConfig.isTurboMode() ? "  TURBO" : "");
        String secondLine = "Q " + pending + "  A " + active + "  C " + compactNumber(cache.size());
        String debugLine = "hits " + Math.round(cache.getHitRate()) + "%  queued " + queued;

        int contentW = Math.max(font.width(title), Math.max(font.width(firstLine), font.width(secondLine)));
        if (debug) contentW = Math.max(contentW, font.width(debugLine));

        int width = Math.max(118, contentW + PADDING_X * 2 + 12);
        int lines = debug ? 4 : 3;
        int height = PADDING_Y * 2 + lines * LINE_H + (busy ? 5 : 0);

        int x = graphics.guiWidth() - width - MARGIN;
        int y = graphics.guiHeight() - height - MARGIN - 28;
        if (x < MARGIN) x = MARGIN;
        if (y < MARGIN) y = MARGIN;

        graphics.fill(x, y, x + width, y + height, BG_BOTTOM);
        graphics.fillGradient(x, y, x + width, y + Math.max(10, height / 2), BG_TOP, BG_BOTTOM);
        drawBorder(graphics, x, y, width, height, BORDER);

        int dotColor = !enabled ? COLOR_DISABLED : busy ? COLOR_BUSY : COLOR_ENABLED;
        int textX = x + PADDING_X;
        int textY = y + PADDING_Y;

        graphics.fill(textX, textY + 3, textX + DOT, textY + 3 + DOT, dotColor);
        graphics.drawString(font, title, textX + 10, textY, COLOR_TITLE, false);

        int y2 = textY + LINE_H;
        graphics.drawString(font, firstLine, textX, y2,
            !enabled ? COLOR_DISABLED : busy ? COLOR_BUSY : COLOR_ENABLED, false);

        int y3 = y2 + LINE_H;
        graphics.drawString(font, secondLine, textX, y3,
            fallback ? COLOR_FALLBACK : COLOR_MUTED, false);

        if (debug) {
            graphics.drawString(font, debugLine, textX, y3 + LINE_H, COLOR_MUTED, false);
        }

        if (busy) {
            int barX = x + PADDING_X;
            int barY = y + height - 5;
            int barW = width - PADDING_X * 2;
            int fillW = busyBarWidth(barW, pending, queued, active);
            graphics.fill(barX, barY, barX + barW, barY + 2, BAR_BG);
            graphics.fill(barX, barY, barX + fillW, barY + 2, dotColor);
        }
    }

    private static void drawBorder(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x, y, x + width, y + 1, color);
        graphics.fill(x, y + height - 1, x + width, y + height, color);
        graphics.fill(x, y, x + 1, y + height, color);
        graphics.fill(x + width - 1, y, x + width, y + height, color);
    }

    private static int busyBarWidth(int width, int pending, int queued, int active) {
        int total = Math.max(1, pending + queued + active);
        int visible = Math.max(1, active + Math.min(queued, 64));
        return Math.max(2, Math.min(width, width * visible / Math.max(visible, total)));
    }

    private static String shortEngine(String engine) {
        if (engine == null || engine.isBlank()) return "Engine";
        int space = engine.indexOf(' ');
        return space > 0 ? engine.substring(0, space) : engine;
    }

    private static String compactNumber(int value) {
        if (value >= 1_000_000) return value / 1_000_000 + "M";
        if (value >= 10_000) return value / 1_000 + "K";
        return Integer.toString(value);
    }
}
