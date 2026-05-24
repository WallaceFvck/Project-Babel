package com.projectbabel.ui.cache;

import com.projectbabel.core.cache.TranslationCacheEntry;
import com.projectbabel.core.service.TranslationManager;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static com.projectbabel.ui.cache.CacheScreenTheme.ENTRY_HEIGHT;
import static com.projectbabel.ui.cache.CacheScreenTheme.HEADER_HEIGHT;

/**
 * State and mutations for the cache list.
 * The Screen should own widgets only; filtering, selection and scroll live here.
 */
public final class CacheScreenModel {
    private List<TranslationCacheEntry> allEntries = new ArrayList<>();
    private List<TranslationCacheEntry> filteredEntries = new ArrayList<>();
    private int scrollOffset = 0;
    private int maxScroll = 0;
    private String query = "";
    private TranslationCacheEntry selectedEntry;

    public void refresh(int screenHeight) {
        allEntries = TranslationManager.getInstance().getCache().getAllEntries();
        filter(screenHeight, false);
        reconcileSelection();
    }

    public void setQuery(String query, int screenHeight) {
        String normalized = query == null ? "" : query;
        if (normalized.equals(this.query)) return;
        this.query = normalized;
        this.scrollOffset = 0;
        filter(screenHeight, true);
    }

    public void select(TranslationCacheEntry entry) {
        selectedEntry = entry;
    }

    public void clearSelection() {
        selectedEntry = null;
    }

    public void deleteEntry(TranslationCacheEntry entry, int screenHeight) {
        if (entry == null) return;
        TranslationManager.getInstance().getCache().removeByKey(entry.key());
        if (selectedEntry != null && selectedEntry.key().equals(entry.key())) {
            clearSelection();
        }
        refresh(screenHeight);
    }

    public void saveSelected(String translatedText, int screenHeight) {
        if (selectedEntry == null) return;
        String selectedKey = selectedEntry.key();
        TranslationManager.getInstance().getCache().updateByKey(selectedKey, translatedText);
        refresh(screenHeight);
        for (TranslationCacheEntry entry : filteredEntries) {
            if (entry.key().equals(selectedKey)) {
                select(entry);
                return;
            }
        }
        clearSelection();
    }

    public int visibleRows(int screenHeight) {
        return Math.max(1, (screenHeight - HEADER_HEIGHT) / ENTRY_HEIGHT);
    }

    public void scrollByRows(int rows) {
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset + rows));
    }

    public void scrollByMouseDelta(double delta) {
        scrollByRows(-(int) (delta * 3));
    }

    public void recalcScroll(int screenHeight) {
        maxScroll = Math.max(0, filteredEntries.size() - visibleRows(screenHeight));
        scrollOffset = Math.min(scrollOffset, maxScroll);
    }

    private void filter(int screenHeight, boolean preserveSelection) {
        if (query.isBlank()) {
            filteredEntries = new ArrayList<>(allEntries);
        } else {
            String lower = query.toLowerCase();
            filteredEntries = allEntries.stream()
                .filter(e -> e.original().toLowerCase().contains(lower)
                          || e.translated().toLowerCase().contains(lower))
                .collect(Collectors.toList());
        }
        recalcScroll(screenHeight);
        if (!preserveSelection) reconcileSelection();
    }

    private void reconcileSelection() {
        if (selectedEntry == null) return;
        for (TranslationCacheEntry entry : allEntries) {
            if (entry.key().equals(selectedEntry.key())) {
                selectedEntry = entry;
                return;
            }
        }
        selectedEntry = null;
    }

    public List<TranslationCacheEntry> allEntries() {
        return allEntries;
    }

    public List<TranslationCacheEntry> filteredEntries() {
        return filteredEntries;
    }

    public int allSize() {
        return allEntries.size();
    }

    public int filteredSize() {
        return filteredEntries.size();
    }

    public int scrollOffset() {
        return scrollOffset;
    }

    public int maxScroll() {
        return maxScroll;
    }

    public String query() {
        return query;
    }

    public TranslationCacheEntry selectedEntry() {
        return selectedEntry;
    }
}
