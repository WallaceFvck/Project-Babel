package com.projectbabel.ui.cache;

import com.projectbabel.ProjectBabelCommon;
import com.projectbabel.core.dictionary.UniversalTermsDictionary;
import com.projectbabel.core.service.TranslationManager;
import com.projectbabel.core.text.BabelI18n;
import com.projectbabel.core.guard.RenderingGuard;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;

import static com.projectbabel.ui.cache.CacheScreenTheme.*;

/** Sidebar status and button-backplate renderer. */
public final class CacheSidebarPanel {
    private CacheSidebarPanel() {}

    public static void renderButtonBackgrounds(
        GuiGraphics g,
        Button btnToggle,
        Button btnHud,
        Button btnTurbo,
        Button btnUniversalTerms,
        Button btnUniversalSource,
        Button btnDebug
    ) {
        fillButtonBackplate(g, btnToggle, ProjectBabelCommon.config().isEnabled() ? C_BTN_ON : C_BTN_OFF);
        fillButtonBackplate(g, btnHud, ProjectBabelCommon.config().isShowHudIndicator() ? C_BTN_HUD_ON : C_BTN_HUD_OFF);
        fillButtonBackplate(g, btnTurbo, ProjectBabelCommon.config().isTurboMode() ? C_BTN_ON : C_BTN_OFF);
        fillButtonBackplate(g, btnUniversalTerms, ProjectBabelCommon.config().isUniversalTermsEnabled() ? C_BTN_ON : C_BTN_OFF);
        fillButtonBackplate(g, btnUniversalSource, ProjectBabelCommon.config().isUniversalTermsRemote() ? C_BTN_HUD_ON : C_BTN_HUD_OFF);
        fillButtonBackplate(g, btnDebug, !"off".equals(ProjectBabelCommon.config().getDebugScope()) ? C_WARN : C_BTN_HUD_OFF);
    }

    public static void renderInfo(
        Font font,
        GuiGraphics g,
        CacheScreenModel model,
        EditBox sourceLangBox,
        EditBox targetLangBox,
        int langsLabelY,
        int engineInfoY,
        int universalInfoY
    ) {
        int x = PADDING;
        int bw = SIDEBAR_W - PADDING * 2;
        TranslationManager manager = TranslationManager.getInstance();
        var cache = manager.getCache();
        boolean fallback = manager.isUsingFallback();
        String engineName = manager.getActiveEngineName();
        int engineColor = fallback ? C_ENGINE_FB : C_ENGINE_OK;

        RenderingGuard.enter();
        try {
            g.drawString(font, "Project", x, 8, C_LABEL, false);
            g.drawString(font, "Babel", x, 18, C_TITLE, false);
            g.fill(x, 32, SIDEBAR_W - PADDING, 33, C_BORDER);

            int boxY = 38;
            int boxW = (bw - 4) / 2;
            drawStatusCard(font, g, x, boxY, boxW, 20,
                "Cache", compactNumber(cache.size()), C_TRANSLATED);
            drawStatusCard(font, g, x + boxW + 4, boxY, boxW, 20,
                "Lista", compactNumber(model.filteredSize()) + "/" + compactNumber(model.allSize()), C_TITLE);
            drawStatusCard(font, g, x, boxY + 24, boxW, 20,
                "Fila", compactNumber(manager.getPendingCount()), manager.getPendingCount() > 0 ? C_WARN : C_ACCENT);
            drawStatusCard(font, g, x + boxW + 4, boxY + 24, boxW, 20,
                "Hits", Math.round(cache.getHitRate()) + "%", C_STATS);

            if (sourceLangBox != null && targetLangBox != null) {
                g.drawString(font, BabelI18n.t("source.short"), sourceLangBox.getX(), langsLabelY, C_LABEL, false);
                g.drawString(font, BabelI18n.t("target.short"), targetLangBox.getX(), langsLabelY, C_LABEL, false);
            }

            g.drawString(font,
                truncate(font, BabelI18n.t("engine") + ": " + engineName, bw),
                x, engineInfoY, engineColor, false);
            g.drawString(font,
                "Req: " + manager.getActiveTranslationCount()
                    + "/" + manager.getActiveConcurrencyLimit(),
                x, engineInfoY + 10, C_STATS, false);

            UniversalTermsDictionary glossary = UniversalTermsDictionary.getInstance();
            g.drawString(font,
                truncate(font, glossary.statusSummary(), bw),
                x, universalInfoY,
                ProjectBabelCommon.config().isUniversalTermsEnabled() ? C_TRANSLATED : C_STATS,
                false);
        } finally {
            RenderingGuard.exit();
        }
    }

    private static void drawStatusCard(
        Font font,
        GuiGraphics g,
        int x,
        int y,
        int w,
        int h,
        String label,
        String value,
        int valueColor
    ) {
        g.fill(x, y, x + w, y + h, C_PANEL);
        drawBoxBorder(g, x, y, w, h, 0x773B4658);
        g.drawString(font, truncate(font, label, w - 8), x + 4, y + 2, C_LABEL, false);
        g.drawString(font, truncate(font, value, w - 8), x + 4, y + 11, valueColor, false);
    }
}
