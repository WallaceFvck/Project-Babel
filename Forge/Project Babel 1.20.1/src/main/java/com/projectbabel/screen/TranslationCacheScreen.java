package com.projectbabel.screen;

import com.projectbabel.config.AutoTranslateConfig;
import com.projectbabel.translation.BabelI18n;
import com.projectbabel.translation.FTBQuestChapterPreloader;
import com.projectbabel.translation.FTBQuestFirstOpenTracker;
import com.projectbabel.translation.FTBQuestSidebarPreloader;
import com.projectbabel.translation.GuideMePreloader;
import com.projectbabel.translation.ModonomiconBookPreloader;
import com.projectbabel.translation.PatchouliBookPreloader;
import com.projectbabel.translation.RenderingGuard;
import com.projectbabel.translation.TranslationManager;
import com.projectbabel.translation.TranslationPipeline;
import com.projectbabel.translation.TranslationSkipRegistry;
import com.projectbabel.translation.UniversalTermsDictionary;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Menu principal do projectbabel — aberto com a tecla H.
 *
 * Sidebar esquerda (controles):
 * ✔/✘  Tradução ON/OFF
 * HUD  ON/OFF
 * Engine: Google / Lingva (fallback) + botão "Forçar Google"
 * Limpar Cache
 * Fechar
 *
 * Área principal (direita): histórico de traduções com busca e scroll.
 */
public class TranslationCacheScreen extends Screen {

    // ── Dimensões ──────────────────────────────────────────────────────
    private static final int SIDEBAR_W     = 170; // Aumentado para melhor acomodar textos
    private static final int ENTRY_HEIGHT  = 24;
    private static final int HEADER_HEIGHT = 86;  // Reduzido para economizar espaço vertical
    private static final int PADDING       = 6;
    private static final int SCROLLBAR_W   = 6;

    // ── Paleta ─────────────────────────────────────────────────────────
    private static final int C_BG           = 0xD9080A10;
    private static final int C_SIDEBAR_BG   = 0xE00E1118;
    private static final int C_HEADER_TOP   = 0xE0141821;
    private static final int C_HEADER_BOT   = 0xE00B0D13;
    private static final int C_PANEL        = 0xAA151A24;
    private static final int C_PANEL_DARK   = 0xAA0D1118;
    private static final int C_BORDER       = 0xFF303746;
    private static final int C_ENTRY_ODD    = 0x8810141C;
    private static final int C_ENTRY_EVEN   = 0x88191E28;
    private static final int C_ENTRY_HOVER  = 0x3347A3FF;
    private static final int C_ENTRY_SELECT = 0x445FF090;
    private static final int C_ORIGINAL     = 0xFFD2D8E3;
    private static final int C_ARROW        = 0xFF617087;
    private static final int C_TRANSLATED   = 0xFF7CF0A2;
    private static final int C_TITLE        = 0xFFE7EDF7;
    private static final int C_STATS        = 0xFF8F9AAB;
    private static final int C_SCROLLBAR    = 0xAA7E8CA6;
    private static final int C_SCROLLBAR_BG = 0x33262B36;
    private static final int C_EMPTY        = 0xFF667085;
    private static final int C_LABEL        = 0xFF9AA6BB;
    private static final int C_ACCENT       = 0xFF5FF090;
    private static final int C_WARN         = 0xFFFFC857;
    private static final int C_BTN_ON       = 0xAA1A6632;
    private static final int C_BTN_OFF      = 0xAA661A22;
    private static final int C_BTN_HUD_ON   = 0xAA1A3366;
    private static final int C_BTN_HUD_OFF  = 0xAA333344;
    private static final int C_ENGINE_OK    = 0xFFBBDD55;
    private static final int C_ENGINE_FB    = 0xFFFFAA33;

    // ── Estado ─────────────────────────────────────────────────────────
    private final Screen previousScreen;
    private List<CacheEntry> allEntries      = new ArrayList<>();
    private List<CacheEntry> filteredEntries = new ArrayList<>();
    private int scrollOffset = 0;
    private int maxScroll    = 0;

    private EditBox searchBox;
    private EditBox editTranslationBox;
    private EditBox sourceLangBox;
    private EditBox targetLangBox;
    private String  lastQuery = "";
    private CacheEntry selectedEntry;

    private Button btnToggle;
    private Button btnHud;
    private Button btnRenamedItems;
    private Button btnChat;
    private Button btnTurbo;
    private Button btnDebug;
    private Button btnUniversalTerms;
    private Button btnUniversalSource;
    private Button btnUniversalReload;
    private Button btnUniversalOpenLocal;
    private Button btnUniversalSavePath;
    private EditBox universalPathBox;
    private Button btnTargetMode;
    private Button btnSaveLang;
    private Button btnSaveEntry;
    private Button btnResetFallback;
    private Button btnClearCache;
    private Button btnClose;
    
    // Posicionamento dinâmico
    private int engineInfoY = 0;
    private int langsLabelY = 0;
    private int universalInfoY = 0;

    public TranslationCacheScreen(Screen previous) {
        super(Component.literal("projectbabel"));
        this.previousScreen = previous;
    }

    // ── Init ───────────────────────────────────────────────────────────

    @Override
    protected void init() {
        int listX  = SIDEBAR_W + PADDING * 2;
        int searchW = width - listX - PADDING;
        int editY = 28;
        int searchY = 62;
        int fieldH = 16;
        int saveW = 56;

        searchBox = new EditBox(font,
            listX, searchY, searchW, fieldH,
            BabelI18n.c("search"));
        searchBox.setHint(BabelI18n.c("search"));
        searchBox.setMaxLength(100);
        searchBox.setResponder(q -> {
            if (!q.equals(lastQuery)) { lastQuery = q; filterEntries(); scrollOffset = 0; }
        });
        addRenderableWidget(searchBox);

        editTranslationBox = new EditBox(font,
            listX, editY, Math.max(40, searchW - saveW - 8), fieldH,
            BabelI18n.c("edit.hint"));
        editTranslationBox.setHint(BabelI18n.c("edit.hint"));
        editTranslationBox.setMaxLength(4096);
        editTranslationBox.active = false;
        addRenderableWidget(editTranslationBox);

        btnSaveEntry = addRenderableWidget(Button.builder(
            BabelI18n.c("save.entry"),
            btn -> saveSelectedEntry())
            .bounds(listX + searchW - saveW, editY, saveW, fieldH).build());
        btnSaveEntry.active = false;

        buildSidebar();
        refreshEntries();
    }

    private void buildSidebar() {
        int x  = PADDING;
        int bw = SIDEBAR_W - PADDING * 2;
        int y  = 88; // Inicia logo abaixo dos Cards de Status
        int gap = 3;
        int btnH = 16;
        int halfW = (bw - 4) / 2;

        // Tradução ON/OFF
        btnToggle = addRenderableWidget(Button.builder(
            toggleLabel(),
            btn -> { AutoTranslateConfig.setEnabled(!AutoTranslateConfig.isEnabled()); refreshToggle(); })
            .bounds(x, y, bw, btnH).build());
        y += btnH + gap;

        // HUD ON/OFF
        btnHud = addRenderableWidget(Button.builder(
            hudLabel(),
            btn -> { AutoTranslateConfig.setShowHudIndicator(!AutoTranslateConfig.isShowHudIndicator()); refreshHud(); })
            .bounds(x, y, bw, btnH).build());
        y += btnH + gap;

        // Itens Renomeados
        btnRenamedItems = addRenderableWidget(Button.builder(
            renamedItemsLabel(),
            btn -> {
                AutoTranslateConfig.setTranslateRenamedItems(!AutoTranslateConfig.isTranslateRenamedItems());
                TranslationSkipRegistry.clear();
                refreshRenamedItems();
            })
            .bounds(x, y, bw, btnH).build());
        y += btnH + gap;
        
        // Target Mode
        btnTargetMode = addRenderableWidget(Button.builder(
            targetModeLabel(),
            btn -> {
                AutoTranslateConfig.setFollowClientLanguage(!AutoTranslateConfig.isFollowClientLanguage());
                TranslationPipeline.clearContextCache();
                FTBQuestFirstOpenTracker.reset();
                FTBQuestChapterPreloader.reset();
                FTBQuestSidebarPreloader.reset();
                FTBQuestSidebarPreloader.requestWorldPreload();
                PatchouliBookPreloader.resetPreloadState();
                ModonomiconBookPreloader.resetPreloadState();
                GuideMePreloader.reset();
                GuideMePreloader.requestWorldPreload();
                refreshTargetMode();
            })
            .bounds(x, y, bw, btnH).build());
        y += btnH + gap;

        // Chat & Turbo na mesma linha (Economiza espaço)
        btnChat = addRenderableWidget(Button.builder(
            chatLabel(),
            btn -> {
                AutoTranslateConfig.setTranslateChat(!AutoTranslateConfig.isTranslateChat());
                refreshChat();
            })
            .bounds(x, y, halfW, btnH).build());

        btnTurbo = addRenderableWidget(Button.builder(
            turboLabel(),
            btn -> {
                AutoTranslateConfig.setTurboMode(!AutoTranslateConfig.isTurboMode());
                refreshTurbo();
            })
            .bounds(x + halfW + 4, y, halfW, btnH).build());
        y += btnH + gap;

        btnDebug = addRenderableWidget(Button.builder(
            debugLabel(),
            btn -> {
                AutoTranslateConfig.cycleDebugScope();
                refreshDebug();
            })
            .bounds(x, y, bw, btnH).build());
        y += btnH + gap;

        // Glossario universal: dicionario fixo antes da API
        btnUniversalTerms = addRenderableWidget(Button.builder(
            universalTermsLabel(),
            btn -> {
                AutoTranslateConfig.setUniversalTermsEnabled(!AutoTranslateConfig.isUniversalTermsEnabled());
                UniversalTermsDictionary.getInstance().reloadAsync();
                refreshUniversalTermsControls();
            })
            .bounds(x, y, halfW, btnH).build());

        btnUniversalSource = addRenderableWidget(Button.builder(
            universalSourceLabel(),
            btn -> {
                AutoTranslateConfig.setUniversalTermsRemote(!AutoTranslateConfig.isUniversalTermsRemote());
                UniversalTermsDictionary.getInstance().reloadAsync();
                refreshUniversalTermsControls();
            })
            .bounds(x + halfW + 4, y, halfW, btnH).build());
        y += btnH + gap;

        universalPathBox = addRenderableWidget(new EditBox(font,
            x, y, Math.max(42, bw - 42), btnH,
            Component.literal("dicionario local")));
        universalPathBox.setMaxLength(512);
        universalPathBox.setValue(AutoTranslateConfig.getUniversalTermsLocalPath());

        btnUniversalSavePath = addRenderableWidget(Button.builder(
            Component.literal("Usar"),
            btn -> saveUniversalLocalPath())
            .bounds(x + bw - 38, y, 38, btnH).build());
        y += btnH + gap;

        btnUniversalReload = addRenderableWidget(Button.builder(
            Component.literal("Recar."),
            btn -> UniversalTermsDictionary.getInstance().reloadAsync())
            .bounds(x, y, halfW, btnH).build());

        btnUniversalOpenLocal = addRenderableWidget(Button.builder(
            Component.literal("Abrir local"),
            btn -> UniversalTermsDictionary.getInstance().createOrOpenLocalFile())
            .bounds(x + halfW + 4, y, halfW, btnH).build());
        y += btnH + gap;
        this.universalInfoY = y;
        y += 10;

        refreshUniversalTermsControls();

        // Idiomas lado a lado
        this.langsLabelY = y;
        y += 10; // Espaço para desenhar a label em cima

        int langW = 44;
        sourceLangBox = addRenderableWidget(new EditBox(font,
            x, y, langW, btnH,
            Component.literal("origem")));
        sourceLangBox.setMaxLength(8);
        sourceLangBox.setValue(AutoTranslateConfig.getSourceLang());

        targetLangBox = addRenderableWidget(new EditBox(font,
            x + langW + 4, y, langW, btnH,
            Component.literal("alvo")));
        targetLangBox.setMaxLength(8);
        targetLangBox.setValue(AutoTranslateConfig.getTargetLang());

        btnSaveLang = addRenderableWidget(Button.builder(
            BabelI18n.c("save.lang"),
            btn -> saveLanguageConfig())
            .bounds(x + langW * 2 + 8, y, bw - (langW * 2 + 8), btnH).build());
        y += btnH + gap;

        // Texto da Engine
        this.engineInfoY = y;
        y += 22;

        // Forçar Google
        btnResetFallback = addRenderableWidget(Button.builder(
            Component.literal("R  " + BabelI18n.t("force.google")),
            btn -> {
                TranslationManager.getInstance().resetEngineFallback();
                refreshFallbackButton();
            })
            .bounds(x, y, bw, btnH).build());
        y += btnH + gap;

        // Calculando posições inferiores dinâmicas (Impede de encavalar se a tela for pequena)
        int bottomY = Math.max(y + gap, height - PADDING - (btnH * 2 + gap));
        
        btnClearCache = addRenderableWidget(Button.builder(
            BabelI18n.c("clear.cache"),
            btn -> clearCacheAndRestartPreloads())
            .bounds(x, bottomY, bw, btnH).build());

        btnClose = addRenderableWidget(Button.builder(
            BabelI18n.c("close"),
            btn -> onClose())
            .bounds(x, bottomY + btnH + gap, bw, btnH).build());

        refreshFallbackButton();
    }

    // ── Refresh helpers ────────────────────────────────────────────────

    private Component toggleLabel() {
        return AutoTranslateConfig.isEnabled()
            ? BabelI18n.c("translation.on")
            : BabelI18n.c("translation.off");
    }

    private Component hudLabel() {
        return AutoTranslateConfig.isShowHudIndicator()
            ? BabelI18n.c("hud.on")
            : BabelI18n.c("hud.off");
    }

    private Component targetModeLabel() {
        return AutoTranslateConfig.isFollowClientLanguage()
            ? BabelI18n.c("target.client")
            : BabelI18n.c("target.config");
    }

    private Component renamedItemsLabel() {
        return AutoTranslateConfig.isTranslateRenamedItems()
            ? BabelI18n.c("renamed.on")
            : BabelI18n.c("renamed.off");
    }

    private Component chatLabel() {
        return AutoTranslateConfig.isTranslateChat()
            ? BabelI18n.c("chat.on")
            : BabelI18n.c("chat.off");
    }

    private Component turboLabel() {
        return AutoTranslateConfig.isTurboMode()
            ? BabelI18n.c("turbo.on")
            : BabelI18n.c("turbo.off");
    }

    private Component debugLabel() {
        return BabelI18n.c("debug." + AutoTranslateConfig.getDebugScope());
    }

    private Component universalTermsLabel() {
        return Component.literal(AutoTranslateConfig.isUniversalTermsEnabled()
            ? "Glossario ON"
            : "Glossario OFF");
    }

    private Component universalSourceLabel() {
        return Component.literal(AutoTranslateConfig.isUniversalTermsRemote()
            ? "Fonte: Web"
            : "Fonte: Local");
    }

    private void refreshToggle()        { btnToggle.setMessage(toggleLabel()); }
    private void refreshHud()           { btnHud.setMessage(hudLabel()); }
    private void refreshRenamedItems()  { btnRenamedItems.setMessage(renamedItemsLabel()); }
    private void refreshChat()          { btnChat.setMessage(chatLabel()); }
    private void refreshTurbo()         { btnTurbo.setMessage(turboLabel()); }
    private void refreshDebug()         { btnDebug.setMessage(debugLabel()); }
    private void refreshTargetMode()    { btnTargetMode.setMessage(targetModeLabel()); }

    private void refreshUniversalTermsControls() {
        if (btnUniversalTerms != null) btnUniversalTerms.setMessage(universalTermsLabel());
        if (btnUniversalSource != null) btnUniversalSource.setMessage(universalSourceLabel());
        boolean enabled = AutoTranslateConfig.isUniversalTermsEnabled();
        boolean local = !AutoTranslateConfig.isUniversalTermsRemote();
        if (btnUniversalReload != null) btnUniversalReload.active = enabled;
        if (btnUniversalOpenLocal != null) btnUniversalOpenLocal.active = enabled && local;
        if (btnUniversalSavePath != null) btnUniversalSavePath.active = enabled;
        if (universalPathBox != null) {
            universalPathBox.active = enabled && local;
            universalPathBox.setEditable(enabled && local);
        }
    }

    private void saveUniversalLocalPath() {
        if (universalPathBox == null) return;
        AutoTranslateConfig.setUniversalTermsLocalPath(universalPathBox.getValue());
        AutoTranslateConfig.setUniversalTermsRemote(false);
        UniversalTermsDictionary.getInstance().reloadAsync();
        refreshUniversalTermsControls();
    }

    private void saveLanguageConfig() {
        AutoTranslateConfig.setSourceLang(sourceLangBox.getValue());
        AutoTranslateConfig.setTargetLang(targetLangBox.getValue());
        sourceLangBox.setValue(AutoTranslateConfig.getSourceLang());
        targetLangBox.setValue(AutoTranslateConfig.getTargetLang());
        TranslationPipeline.clearContextCache();
        FTBQuestFirstOpenTracker.reset();
        FTBQuestChapterPreloader.reset();
        FTBQuestSidebarPreloader.reset();
        FTBQuestSidebarPreloader.requestWorldPreload();
        PatchouliBookPreloader.resetPreloadState();
        ModonomiconBookPreloader.resetPreloadState();
        GuideMePreloader.reset();
        GuideMePreloader.requestWorldPreload();
        TranslationManager.getInstance().resetEngineFallback();
        UniversalTermsDictionary.getInstance().reloadAsync();
    }

    private void clearCacheAndRestartPreloads() {
        TranslationManager.getInstance().getCache().clear();
        TranslationPipeline.clearContextCache();
        TranslationSkipRegistry.clear();
        FTBQuestFirstOpenTracker.reset();
        FTBQuestChapterPreloader.reset();
        FTBQuestSidebarPreloader.reset();
        FTBQuestSidebarPreloader.requestWorldPreload();
        PatchouliBookPreloader.resetPreloadState();
        ModonomiconBookPreloader.resetPreloadState();
        GuideMePreloader.reset();
        GuideMePreloader.requestWorldPreload();
        UniversalTermsDictionary.getInstance().reloadAsync();

        refreshEntries();
    }

    private void refreshFallbackButton() {
        boolean fallback = TranslationManager.getInstance().isUsingFallback();
        btnResetFallback.visible = fallback;
        btnResetFallback.active  = fallback;
    }

    // ── Cache list ─────────────────────────────────────────────────────

    private void refreshEntries() {
        allEntries = TranslationManager.getInstance().getCache().getAllEntries();
        filterEntries();
    }

    private void filterEntries() {
        if (lastQuery.isBlank()) {
            filteredEntries = new ArrayList<>(allEntries);
        } else {
            String lower = lastQuery.toLowerCase();
            filteredEntries = allEntries.stream()
                .filter(e -> e.original().toLowerCase().contains(lower)
                          || e.translated().toLowerCase().contains(lower))
                .collect(Collectors.toList());
        }
        recalcScroll();
    }

    private void recalcScroll() {
        int listH   = height - HEADER_HEIGHT;
        int visible = Math.max(1, listH / ENTRY_HEIGHT);
        maxScroll    = Math.max(0, filteredEntries.size() - visible);
        scrollOffset = Math.min(scrollOffset, maxScroll);
    }

    // ── Render ─────────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, width, height, C_BG);
        g.fill(0, 0, SIDEBAR_W + PADDING, height, C_SIDEBAR_BG);
        g.fill(SIDEBAR_W + PADDING, 0, SIDEBAR_W + PADDING + 1, height, C_BORDER);
        g.fillGradient(0, 0, width, HEADER_HEIGHT, C_HEADER_TOP, C_HEADER_BOT);

        renderHeader(g);
        renderButtonBackgrounds(g);
        super.render(g, mouseX, mouseY, delta);
        renderSidebarInfo(g);
        renderList(g, mouseX, mouseY);
    }

    private void renderHeader(GuiGraphics g) {
        g.fill(0, HEADER_HEIGHT - 1, width, HEADER_HEIGHT, C_BORDER);
        RenderingGuard.enter();
        try {
            TranslationManager manager = TranslationManager.getInstance();
            int mainX = SIDEBAR_W + PADDING * 2;
            int mainW = width - mainX - PADDING;

            g.drawString(font, BabelI18n.t("screen.title"), mainX, 8, C_TITLE, false);
            g.drawString(font, statusText(manager), mainX + Math.max(0, Math.min(mainW - 100, 126)), 8,
                AutoTranslateConfig.isEnabled() ? C_ACCENT : C_WARN, false);

            g.drawString(font, BabelI18n.t("edit"), mainX, 18, C_LABEL, false);
            g.drawString(font, BabelI18n.t("search"), mainX, 52, C_LABEL, false);
            g.fill(mainX, HEADER_HEIGHT - 3, mainX + mainW, HEADER_HEIGHT - 2, 0x663A4454);
        } finally { RenderingGuard.exit(); }
    }

    private void drawStatusCard(
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
        g.drawString(font, truncate(label, w - 8), x + 4, y + 2, C_LABEL, false);
        g.drawString(font, truncate(value, w - 8), x + 4, y + 11, valueColor, false);
    }

    private String statusText(TranslationManager manager) {
        if (!AutoTranslateConfig.isEnabled()) return "OFF";
        String engine = manager.isUsingFallback() ? "Lingva" : shortEngine(manager.getActiveEngineName());
        return engine + "  " + (AutoTranslateConfig.isTurboMode() ? "Turbo" : "Normal");
    }

    private void renderButtonBackgrounds(GuiGraphics g) {
        // Usa getWidth e getHeight dinamicamente pra se ajustar ao resize de botões compactos
        int x  = PADDING;
        boolean enabled = AutoTranslateConfig.isEnabled();
        g.fill(x - 1, btnToggle.getY() - 1, x + btnToggle.getWidth() + 1, btnToggle.getY() + btnToggle.getHeight() + 1,
            enabled ? C_BTN_ON : C_BTN_OFF);

        boolean hudOn = AutoTranslateConfig.isShowHudIndicator();
        g.fill(x - 1, btnHud.getY() - 1, x + btnHud.getWidth() + 1, btnHud.getY() + btnHud.getHeight() + 1,
            hudOn ? C_BTN_HUD_ON : C_BTN_HUD_OFF);

        boolean turbo = AutoTranslateConfig.isTurboMode();
        g.fill(btnTurbo.getX() - 1, btnTurbo.getY() - 1, btnTurbo.getX() + btnTurbo.getWidth() + 1, btnTurbo.getY() + btnTurbo.getHeight() + 1,
            turbo ? C_BTN_ON : C_BTN_OFF);

        boolean universal = AutoTranslateConfig.isUniversalTermsEnabled();
        g.fill(btnUniversalTerms.getX() - 1, btnUniversalTerms.getY() - 1, btnUniversalTerms.getX() + btnUniversalTerms.getWidth() + 1, btnUniversalTerms.getY() + btnUniversalTerms.getHeight() + 1,
            universal ? C_BTN_ON : C_BTN_OFF);

        boolean universalRemote = AutoTranslateConfig.isUniversalTermsRemote();
        g.fill(btnUniversalSource.getX() - 1, btnUniversalSource.getY() - 1, btnUniversalSource.getX() + btnUniversalSource.getWidth() + 1, btnUniversalSource.getY() + btnUniversalSource.getHeight() + 1,
            universalRemote ? C_BTN_HUD_ON : C_BTN_HUD_OFF);

        boolean debug = !"off".equals(AutoTranslateConfig.getDebugScope());
        g.fill(btnDebug.getX() - 1, btnDebug.getY() - 1, btnDebug.getX() + btnDebug.getWidth() + 1, btnDebug.getY() + btnDebug.getHeight() + 1,
            debug ? C_WARN : C_BTN_HUD_OFF);
    }

    private void renderSidebarInfo(GuiGraphics g) {
        int x  = PADDING;
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
            drawStatusCard(g, x, boxY, boxW, 20,
                "Cache", compactNumber(cache.size()), C_TRANSLATED);
            drawStatusCard(g, x + boxW + 4, boxY, boxW, 20,
                "Lista", compactNumber(filteredEntries.size()) + "/" + compactNumber(allEntries.size()), C_TITLE);
            drawStatusCard(g, x, boxY + 24, boxW, 20,
                "Fila", compactNumber(manager.getPendingCount()), manager.getPendingCount() > 0 ? C_WARN : C_ACCENT);
            drawStatusCard(g, x + boxW + 4, boxY + 24, boxW, 20,
                "Hits", Math.round(cache.getHitRate()) + "%", C_STATS);

            g.drawString(font, BabelI18n.t("source.short"), sourceLangBox.getX(), langsLabelY, C_LABEL, false);
            g.drawString(font, BabelI18n.t("target.short"), targetLangBox.getX(), langsLabelY, C_LABEL, false);

            g.drawString(font,
                truncate(BabelI18n.t("engine") + ": " + engineName, bw),
                x, engineInfoY, engineColor, false);
            g.drawString(font,
                "Req: " + manager.getActiveTranslationCount()
                    + "/" + manager.getActiveConcurrencyLimit(),
                x, engineInfoY + 10, C_STATS, false);

            UniversalTermsDictionary glossary = UniversalTermsDictionary.getInstance();
            g.drawString(font,
                truncate(glossary.statusSummary(), bw),
                x, universalInfoY,
                AutoTranslateConfig.isUniversalTermsEnabled() ? C_TRANSLATED : C_STATS,
                false);
        } finally { RenderingGuard.exit(); }
    }

    private void renderList(GuiGraphics g, int mouseX, int mouseY) {
        int listX = SIDEBAR_W + PADDING * 2 + 1;
        int listY = HEADER_HEIGHT;
        int listW = width - listX - SCROLLBAR_W - PADDING;
        int listH = height - HEADER_HEIGHT;
        int visible = Math.max(1, listH / ENTRY_HEIGHT);

        if (filteredEntries.isEmpty()) {
            RenderingGuard.enter();
            try {
                String msg = allEntries.isEmpty()
                    ? BabelI18n.t("empty")
                    : BabelI18n.f("no.results", lastQuery);
                g.drawCenteredString(font, msg, listX + listW / 2, listY + listH / 2 - 4, C_EMPTY);
            } finally { RenderingGuard.exit(); }
            return;
        }

        int deleteX = listX + listW - 12;
        int halfW = (listW - 34) / 2;

        RenderingGuard.enter();
        try {
            for (int i = 0; i < visible; i++) {
                int idx    = i + scrollOffset;
                if (idx >= filteredEntries.size()) break;
                CacheEntry e      = filteredEntries.get(idx);
                int        entryY = listY + i * ENTRY_HEIGHT;
                boolean    hover  = mouseX >= listX && mouseX < listX + listW
                                 && mouseY >= entryY && mouseY < entryY + ENTRY_HEIGHT;

                g.fill(listX, entryY, listX + listW, entryY + ENTRY_HEIGHT - 1,
                    selectedEntry != null && selectedEntry.key().equals(e.key())
                        ? C_ENTRY_SELECT
                        : (hover ? C_ENTRY_HOVER : (idx % 2 == 0 ? C_ENTRY_EVEN : C_ENTRY_ODD)));
                g.fill(listX, entryY + ENTRY_HEIGHT - 1, listX + listW, entryY + ENTRY_HEIGHT, 0x332A3140);

                int ty = entryY + (ENTRY_HEIGHT - 8) / 2;
                g.drawString(font, truncate(e.original(),   halfW), listX + 4,          ty, C_ORIGINAL,   false);
                g.drawString(font, "->",                              listX + halfW + 6,  ty, C_ARROW,      false);
                g.drawString(font, truncate(e.translated(), halfW), listX + halfW + 18, ty, C_TRANSLATED, false);
                g.fill(deleteX, entryY + 4, deleteX + 11, entryY + ENTRY_HEIGHT - 5, 0x552A1114);
                g.drawString(font, "X", deleteX + 2, ty, 0xFFFF7777, false);
            }
        } finally { RenderingGuard.exit(); }

        if (filteredEntries.size() > visible) {
            int sbX = listX + listW + 2;
            g.fill(sbX, listY, sbX + SCROLLBAR_W, listY + listH, C_SCROLLBAR_BG);
            float ratio  = maxScroll > 0 ? (float) scrollOffset / maxScroll : 0f;
            int   thumbH = Math.max(16, listH * visible / Math.max(1, filteredEntries.size()));
            int   thumbY = listY + (int) (ratio * (listH - thumbH));
            g.fill(sbX + 1, thumbY, sbX + SCROLLBAR_W - 1, thumbY + thumbH, C_SCROLLBAR);
        }
    }

    // ── Input ──────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;

        int listX = SIDEBAR_W + PADDING * 2 + 1;
        int listY = HEADER_HEIGHT;
        int listW = width - listX - SCROLLBAR_W - PADDING;
        int listH = height - HEADER_HEIGHT;
        if (mouseX < listX || mouseX >= listX + listW || mouseY < listY || mouseY >= listY + listH) {
            return false;
        }

        int row = ((int) mouseY - listY) / ENTRY_HEIGHT;
        int idx = row + scrollOffset;
        if (idx < 0 || idx >= filteredEntries.size()) return false;

        CacheEntry entry = filteredEntries.get(idx);
        int deleteX = listX + listW - 12;
        if (mouseX >= deleteX) {
            TranslationManager.getInstance().getCache().removeByKey(entry.key());
            if (selectedEntry != null && selectedEntry.key().equals(entry.key())) clearSelection();
            refreshEntries();
            return true;
        }

        selectEntry(entry);
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (mx > SIDEBAR_W + PADDING) {
            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int)(delta * 3)));
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        switch (key) {
            case 264 -> { scrollOffset = Math.min(maxScroll, scrollOffset + 1);  return true; }
            case 265 -> { scrollOffset = Math.max(0,         scrollOffset - 1);  return true; }
            case 267 -> { scrollOffset = Math.min(maxScroll, scrollOffset + 10); return true; }
            case 266 -> { scrollOffset = Math.max(0,         scrollOffset - 10); return true; }
            case 256 -> { onClose(); return true; }
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void onClose() {
        assert minecraft != null;
        minecraft.setScreen(previousScreen);
    }

    @Override public boolean isPauseScreen() { return false; }

    private void selectEntry(CacheEntry entry) {
        selectedEntry = entry;
        editTranslationBox.active = true;
        editTranslationBox.setValue(entry.translated());
        btnSaveEntry.active = true;
    }

    private void clearSelection() {
        selectedEntry = null;
        editTranslationBox.setValue("");
        editTranslationBox.active = false;
        btnSaveEntry.active = false;
    }

    private void saveSelectedEntry() {
        if (selectedEntry == null) return;
        TranslationManager.getInstance().getCache().updateByKey(selectedEntry.key(), editTranslationBox.getValue());
        refreshEntries();
        for (CacheEntry entry : filteredEntries) {
            if (entry.key().equals(selectedEntry.key())) {
                selectEntry(entry);
                return;
            }
        }
        clearSelection();
    }

    private void drawBoxBorder(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }

    private String shortEngine(String engine) {
        if (engine == null || engine.isBlank()) return "Engine";
        int space = engine.indexOf(' ');
        return space > 0 ? engine.substring(0, space) : engine;
    }

    private String compactNumber(int value) {
        if (value >= 1_000_000) return value / 1_000_000 + "M";
        if (value >= 10_000) return value / 1_000 + "K";
        return Integer.toString(value);
    }

    private String truncate(String text, int maxPx) {
        if (text == null || text.isEmpty()) return "";
        if (font.width(text) <= maxPx) return text;
        String t = text;
        while (t.length() > 1 && font.width(t + "...") > maxPx) t = t.substring(0, t.length() - 1);
        return t + "...";
    }

    public record CacheEntry(String key, String original, String translated) {}
}
