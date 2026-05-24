package com.projectbabel.ui.cache;

import com.projectbabel.core.cache.TranslationCacheEntry;
import com.projectbabel.ProjectBabelCommon;
import com.projectbabel.core.dictionary.UniversalTermsDictionary;
import com.projectbabel.core.pipeline.TranslationSkipRegistry;
import com.projectbabel.core.service.TranslationManager;
import com.projectbabel.core.text.BabelI18n;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import static com.projectbabel.ui.cache.CacheScreenTheme.*;

/**
 * Project Babel control panel and translation-cache editor.
 *
 * Phase 9 keeps this class as the public Screen, but moves cache state,
 * command actions, list rendering and sidebar/header rendering to helpers.
 */
public class TranslationCacheScreen extends Screen {
    private final Screen previousScreen;
    private final CacheScreenModel model = new CacheScreenModel();
    private final CacheEntryListWidget entryList = new CacheEntryListWidget(model);

    private EditBox searchBox;
    private EditBox editTranslationBox;
    private EditBox sourceLangBox;
    private EditBox targetLangBox;
    private EditBox universalPathBox;

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
    private Button btnTargetMode;
    private Button btnSaveLang;
    private Button btnSaveEntry;
    private Button btnResetFallback;
    private Button btnClearCache;
    private Button btnClose;

    private int engineInfoY = 0;
    private int langsLabelY = 0;
    private int universalInfoY = 0;

    public TranslationCacheScreen(Screen previous) {
        super(Component.literal("projectbabel"));
        this.previousScreen = previous;
    }

    @Override
    protected void init() {
        int listX = SIDEBAR_W + PADDING * 2;
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
        searchBox.setResponder(q -> model.setQuery(q, height));
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
        int x = PADDING;
        int bw = SIDEBAR_W - PADDING * 2;
        int y = 88;
        int gap = 3;
        int btnH = 16;
        int halfW = (bw - 4) / 2;

        btnToggle = addRenderableWidget(Button.builder(
            toggleLabel(),
            btn -> {
                ProjectBabelCommon.config().setEnabled(!ProjectBabelCommon.config().isEnabled());
                refreshToggle();
            })
            .bounds(x, y, bw, btnH).build());
        y += btnH + gap;

        btnHud = addRenderableWidget(Button.builder(
            hudLabel(),
            btn -> {
                ProjectBabelCommon.config().setShowHudIndicator(!ProjectBabelCommon.config().isShowHudIndicator());
                refreshHud();
            })
            .bounds(x, y, bw, btnH).build());
        y += btnH + gap;

        btnRenamedItems = addRenderableWidget(Button.builder(
            renamedItemsLabel(),
            btn -> {
                ProjectBabelCommon.config().setTranslateRenamedItems(!ProjectBabelCommon.config().isTranslateRenamedItems());
                TranslationSkipRegistry.clear();
                refreshRenamedItems();
            })
            .bounds(x, y, bw, btnH).build());
        y += btnH + gap;

        btnTargetMode = addRenderableWidget(Button.builder(
            targetModeLabel(),
            btn -> {
                CacheScreenActions.toggleTargetModeAndRestartPreloads();
                refreshTargetMode();
            })
            .bounds(x, y, bw, btnH).build());
        y += btnH + gap;

        btnChat = addRenderableWidget(Button.builder(
            chatLabel(),
            btn -> {
                ProjectBabelCommon.config().setTranslateChat(!ProjectBabelCommon.config().isTranslateChat());
                refreshChat();
            })
            .bounds(x, y, halfW, btnH).build());

        btnTurbo = addRenderableWidget(Button.builder(
            turboLabel(),
            btn -> {
                ProjectBabelCommon.config().setTurboMode(!ProjectBabelCommon.config().isTurboMode());
                refreshTurbo();
            })
            .bounds(x + halfW + 4, y, halfW, btnH).build());
        y += btnH + gap;

        btnDebug = addRenderableWidget(Button.builder(
            debugLabel(),
            btn -> {
                ProjectBabelCommon.config().cycleDebugScope();
                refreshDebug();
            })
            .bounds(x, y, bw, btnH).build());
        y += btnH + gap;

        btnUniversalTerms = addRenderableWidget(Button.builder(
            universalTermsLabel(),
            btn -> {
                ProjectBabelCommon.config().setUniversalTermsEnabled(!ProjectBabelCommon.config().isUniversalTermsEnabled());
                UniversalTermsDictionary.getInstance().reloadAsync();
                refreshUniversalTermsControls();
            })
            .bounds(x, y, halfW, btnH).build());

        btnUniversalSource = addRenderableWidget(Button.builder(
            universalSourceLabel(),
            btn -> {
                ProjectBabelCommon.config().setUniversalTermsRemote(!ProjectBabelCommon.config().isUniversalTermsRemote());
                UniversalTermsDictionary.getInstance().reloadAsync();
                refreshUniversalTermsControls();
            })
            .bounds(x + halfW + 4, y, halfW, btnH).build());
        y += btnH + gap;

        universalPathBox = addRenderableWidget(new EditBox(font,
            x, y, Math.max(42, bw - 42), btnH,
            Component.literal("dicionario local")));
        universalPathBox.setMaxLength(512);
        universalPathBox.setValue(ProjectBabelCommon.config().getUniversalTermsLocalPath());

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

        this.langsLabelY = y;
        y += 10;

        int langW = 44;
        sourceLangBox = addRenderableWidget(new EditBox(font,
            x, y, langW, btnH,
            Component.literal("origem")));
        sourceLangBox.setMaxLength(8);
        sourceLangBox.setValue(ProjectBabelCommon.config().getSourceLang());

        targetLangBox = addRenderableWidget(new EditBox(font,
            x + langW + 4, y, langW, btnH,
            Component.literal("alvo")));
        targetLangBox.setMaxLength(8);
        targetLangBox.setValue(ProjectBabelCommon.config().getTargetLang());

        btnSaveLang = addRenderableWidget(Button.builder(
            BabelI18n.c("save.lang"),
            btn -> saveLanguageConfig())
            .bounds(x + langW * 2 + 8, y, bw - (langW * 2 + 8), btnH).build());
        y += btnH + gap;

        this.engineInfoY = y;
        y += 22;

        btnResetFallback = addRenderableWidget(Button.builder(
            Component.literal("R  " + BabelI18n.t("force.google")),
            btn -> {
                TranslationManager.getInstance().resetEngineFallback();
                refreshFallbackButton();
            })
            .bounds(x, y, bw, btnH).build());
        y += btnH + gap;

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

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, width, height, C_BG);
        g.fill(0, 0, SIDEBAR_W + PADDING, height, C_SIDEBAR_BG);
        g.fill(SIDEBAR_W + PADDING, 0, SIDEBAR_W + PADDING + 1, height, C_BORDER);
        g.fillGradient(0, 0, width, HEADER_HEIGHT, C_HEADER_TOP, C_HEADER_BOT);

        CacheHeaderPanel.render(font, g, width);
        CacheSidebarPanel.renderButtonBackgrounds(
            g,
            btnToggle,
            btnHud,
            btnTurbo,
            btnUniversalTerms,
            btnUniversalSource,
            btnDebug
        );
        super.render(g, mouseX, mouseY, delta);
        CacheSidebarPanel.renderInfo(
            font,
            g,
            model,
            sourceLangBox,
            targetLangBox,
            langsLabelY,
            engineInfoY,
            universalInfoY
        );
        entryList.render(font, g, width, height, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        return entryList.mouseClicked(mouseX, mouseY, width, height, this::syncSelectionWidgets);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (entryList.mouseScrolled(mx, delta)) return true;
        return super.mouseScrolled(mx, my, delta);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (entryList.keyPressed(key)) return true;
        if (key == 256) {
            onClose();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void onClose() {
        assert minecraft != null;
        minecraft.setScreen(previousScreen);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private Component toggleLabel() {
        return ProjectBabelCommon.config().isEnabled()
            ? BabelI18n.c("translation.on")
            : BabelI18n.c("translation.off");
    }

    private Component hudLabel() {
        return ProjectBabelCommon.config().isShowHudIndicator()
            ? BabelI18n.c("hud.on")
            : BabelI18n.c("hud.off");
    }

    private Component targetModeLabel() {
        return ProjectBabelCommon.config().isFollowClientLanguage()
            ? BabelI18n.c("target.client")
            : BabelI18n.c("target.config");
    }

    private Component renamedItemsLabel() {
        return ProjectBabelCommon.config().isTranslateRenamedItems()
            ? BabelI18n.c("renamed.on")
            : BabelI18n.c("renamed.off");
    }

    private Component chatLabel() {
        return ProjectBabelCommon.config().isTranslateChat()
            ? BabelI18n.c("chat.on")
            : BabelI18n.c("chat.off");
    }

    private Component turboLabel() {
        return ProjectBabelCommon.config().isTurboMode()
            ? BabelI18n.c("turbo.on")
            : BabelI18n.c("turbo.off");
    }

    private Component debugLabel() {
        return BabelI18n.c("debug." + ProjectBabelCommon.config().getDebugScope());
    }

    private Component universalTermsLabel() {
        return Component.literal(ProjectBabelCommon.config().isUniversalTermsEnabled()
            ? "Glossario ON"
            : "Glossario OFF");
    }

    private Component universalSourceLabel() {
        return Component.literal(ProjectBabelCommon.config().isUniversalTermsRemote()
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
        boolean enabled = ProjectBabelCommon.config().isUniversalTermsEnabled();
        boolean local = !ProjectBabelCommon.config().isUniversalTermsRemote();
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
        CacheScreenActions.saveUniversalLocalPath(universalPathBox.getValue());
        universalPathBox.setValue(ProjectBabelCommon.config().getUniversalTermsLocalPath());
        refreshUniversalTermsControls();
    }

    private void saveLanguageConfig() {
        CacheScreenActions.saveLanguageConfig(sourceLangBox.getValue(), targetLangBox.getValue());
        sourceLangBox.setValue(ProjectBabelCommon.config().getSourceLang());
        targetLangBox.setValue(ProjectBabelCommon.config().getTargetLang());
        refreshFallbackButton();
    }

    private void clearCacheAndRestartPreloads() {
        CacheScreenActions.clearCacheAndRestartPreloads();
        refreshEntries();
    }

    private void refreshFallbackButton() {
        if (btnResetFallback == null) return;
        boolean fallback = TranslationManager.getInstance().isUsingFallback();
        btnResetFallback.visible = fallback;
        btnResetFallback.active = fallback;
    }

    private void refreshEntries() {
        model.refresh(height);
        syncSelectionWidgets();
    }

    private void saveSelectedEntry() {
        if (editTranslationBox == null) return;
        model.saveSelected(editTranslationBox.getValue(), height);
        syncSelectionWidgets();
    }

    private void syncSelectionWidgets() {
        if (editTranslationBox == null || btnSaveEntry == null) return;
        TranslationCacheEntry selected = model.selectedEntry();
        if (selected == null) {
            editTranslationBox.setValue("");
            editTranslationBox.active = false;
            btnSaveEntry.active = false;
            return;
        }
        editTranslationBox.active = true;
        editTranslationBox.setValue(selected.translated());
        btnSaveEntry.active = true;
    }
}
