package com.projectbabel.ui.cache;

import com.projectbabel.ProjectBabelCommon;
import com.projectbabel.core.service.TranslationManager;
import com.projectbabel.core.text.BabelI18n;
import com.projectbabel.core.guard.RenderingGuard;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import static com.projectbabel.ui.cache.CacheScreenTheme.*;

/** Header renderer for the cache screen. */
public final class CacheHeaderPanel {
    private CacheHeaderPanel() {}

    public static void render(Font font, GuiGraphics g, int screenWidth) {
        g.fill(0, HEADER_HEIGHT - 1, screenWidth, HEADER_HEIGHT, C_BORDER);
        RenderingGuard.enter();
        try {
            TranslationManager manager = TranslationManager.getInstance();
            int mainX = SIDEBAR_W + PADDING * 2;
            int mainW = screenWidth - mainX - PADDING;

            g.drawString(font, BabelI18n.t("screen.title"), mainX, 8, C_TITLE, false);
            g.drawString(font, statusText(manager), mainX + Math.max(0, Math.min(mainW - 100, 126)), 8,
                ProjectBabelCommon.config().isEnabled() ? C_ACCENT : C_WARN, false);

            g.drawString(font, BabelI18n.t("edit"), mainX, 18, C_LABEL, false);
            g.drawString(font, BabelI18n.t("search"), mainX, 52, C_LABEL, false);
            g.fill(mainX, HEADER_HEIGHT - 3, mainX + mainW, HEADER_HEIGHT - 2, 0x663A4454);
        } finally {
            RenderingGuard.exit();
        }
    }

    private static String statusText(TranslationManager manager) {
        if (!ProjectBabelCommon.config().isEnabled()) return "OFF";
        String engine = manager.isUsingFallback() ? "Lingva" : shortEngine(manager.getActiveEngineName());
        return engine + "  " + (ProjectBabelCommon.config().isTurboMode() ? "Turbo" : "Normal");
    }
}
