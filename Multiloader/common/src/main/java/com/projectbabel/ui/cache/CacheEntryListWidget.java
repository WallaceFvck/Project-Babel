package com.projectbabel.ui.cache;

import com.projectbabel.core.cache.TranslationCacheEntry;
import com.projectbabel.core.text.BabelI18n;
import com.projectbabel.core.guard.RenderingGuard;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;

import static com.projectbabel.ui.cache.CacheScreenTheme.*;

/** Renders and handles the right-side cache-entry list. */
public final class CacheEntryListWidget {
    private final CacheScreenModel model;

    public CacheEntryListWidget(CacheScreenModel model) {
        this.model = model;
    }

    public void render(Font font, GuiGraphics g, int screenWidth, int screenHeight, int mouseX, int mouseY) {
        int listX = listX();
        int listY = listY();
        int listW = listW(screenWidth);
        int listH = listH(screenHeight);
        int visible = model.visibleRows(screenHeight);
        List<TranslationCacheEntry> entries = model.filteredEntries();

        if (entries.isEmpty()) {
            RenderingGuard.enter();
            try {
                String msg = model.allEntries().isEmpty()
                    ? BabelI18n.t("empty")
                    : BabelI18n.f("no.results", model.query());
                g.drawCenteredString(font, msg, listX + listW / 2, listY + listH / 2 - 4, C_EMPTY);
            } finally {
                RenderingGuard.exit();
            }
            return;
        }

        int deleteX = listX + listW - 12;
        int halfW = (listW - 34) / 2;
        TranslationCacheEntry selected = model.selectedEntry();

        RenderingGuard.enter();
        try {
            for (int i = 0; i < visible; i++) {
                int idx = i + model.scrollOffset();
                if (idx >= entries.size()) break;
                TranslationCacheEntry entry = entries.get(idx);
                int entryY = listY + i * ENTRY_HEIGHT;
                boolean hover = mouseX >= listX && mouseX < listX + listW
                             && mouseY >= entryY && mouseY < entryY + ENTRY_HEIGHT;

                g.fill(listX, entryY, listX + listW, entryY + ENTRY_HEIGHT - 1,
                    selected != null && selected.key().equals(entry.key())
                        ? C_ENTRY_SELECT
                        : (hover ? C_ENTRY_HOVER : (idx % 2 == 0 ? C_ENTRY_EVEN : C_ENTRY_ODD)));
                g.fill(listX, entryY + ENTRY_HEIGHT - 1, listX + listW, entryY + ENTRY_HEIGHT, 0x332A3140);

                int ty = entryY + (ENTRY_HEIGHT - 8) / 2;
                g.drawString(font, truncate(font, entry.original(), halfW), listX + 4, ty, C_ORIGINAL, false);
                g.drawString(font, "->", listX + halfW + 6, ty, C_ARROW, false);
                g.drawString(font, truncate(font, entry.translated(), halfW), listX + halfW + 18, ty, C_TRANSLATED, false);
                g.fill(deleteX, entryY + 4, deleteX + 11, entryY + ENTRY_HEIGHT - 5, 0x552A1114);
                g.drawString(font, "X", deleteX + 2, ty, 0xFFFF7777, false);
            }
        } finally {
            RenderingGuard.exit();
        }

        renderScrollbar(g, screenWidth, screenHeight, visible, entries.size());
    }

    public boolean mouseClicked(double mouseX, double mouseY, int screenWidth, int screenHeight, Runnable onSelectionChanged) {
        int listX = listX();
        int listY = listY();
        int listW = listW(screenWidth);
        int listH = listH(screenHeight);
        if (mouseX < listX || mouseX >= listX + listW || mouseY < listY || mouseY >= listY + listH) {
            return false;
        }

        int row = ((int) mouseY - listY) / ENTRY_HEIGHT;
        int idx = row + model.scrollOffset();
        List<TranslationCacheEntry> entries = model.filteredEntries();
        if (idx < 0 || idx >= entries.size()) return false;

        TranslationCacheEntry entry = entries.get(idx);
        int deleteX = listX + listW - 12;
        if (mouseX >= deleteX) {
            model.deleteEntry(entry, screenHeight);
            onSelectionChanged.run();
            return true;
        }

        model.select(entry);
        onSelectionChanged.run();
        return true;
    }

    public boolean mouseScrolled(double mx, double delta) {
        if (mx <= SIDEBAR_W + PADDING) return false;
        model.scrollByMouseDelta(delta);
        return true;
    }

    public boolean keyPressed(int key) {
        switch (key) {
            case 264 -> { model.scrollByRows(1); return true; }
            case 265 -> { model.scrollByRows(-1); return true; }
            case 267 -> { model.scrollByRows(10); return true; }
            case 266 -> { model.scrollByRows(-10); return true; }
            default -> { return false; }
        }
    }

    private void renderScrollbar(GuiGraphics g, int screenWidth, int screenHeight, int visible, int totalEntries) {
        if (totalEntries <= visible) return;
        int listX = listX();
        int listY = listY();
        int listW = listW(screenWidth);
        int listH = listH(screenHeight);
        int sbX = listX + listW + 2;
        g.fill(sbX, listY, sbX + SCROLLBAR_W, listY + listH, C_SCROLLBAR_BG);
        float ratio = model.maxScroll() > 0 ? (float) model.scrollOffset() / model.maxScroll() : 0f;
        int thumbH = Math.max(16, listH * visible / Math.max(1, totalEntries));
        int thumbY = listY + (int) (ratio * (listH - thumbH));
        g.fill(sbX + 1, thumbY, sbX + SCROLLBAR_W - 1, thumbY + thumbH, C_SCROLLBAR);
    }
}
