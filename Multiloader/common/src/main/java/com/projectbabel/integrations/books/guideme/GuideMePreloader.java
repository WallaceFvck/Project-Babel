package com.projectbabel.integrations.books.guideme;

import com.projectbabel.api.TranslationContext;
import com.projectbabel.integrations.books.BookIdentity;
import com.projectbabel.integrations.books.BookPreloadAdapter;
import com.projectbabel.integrations.books.BookPreloadContext;
import com.projectbabel.integrations.books.BookPreloadCoordinator;
import com.projectbabel.ProjectBabelCommon;
import com.projectbabel.debug.ProjectBabelDebug;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import com.projectbabel.core.text.LanguageDetector;
import com.projectbabel.core.text.TextFilter;
import com.projectbabel.core.text.TextFormatUtils;
import com.projectbabel.core.service.TranslationManager;
import com.projectbabel.core.pipeline.TranslationPipeline;
/**
 * Preloads GuideME/AE2 Guide markdown while the client is entering a world.
 *
 * GuideME lays out markdown text once, so the actual render hook cannot safely
 * translate late without breaking wrapping. This class warms the same literal
 * text fragments that GuideME will later pass into LytFlowText#setText.
 */
public final class GuideMePreloader {

    private static final long PRELOAD_WAIT_TIMEOUT_MS = 120_000L;
    private static final String GUIDE_PRELOAD_SCOPE = "guideme_markdown";
    private static final BookPreloadAdapter ADAPTER = new GuideMeAdapter();

    private GuideMePreloader() {}

    public static void requestWorldPreload() {
        if (!isGuideMeAvailable()) return;
        if (!LanguageDetector.shouldModBeActive()) return;

        BookPreloadCoordinator.request(ADAPTER, GUIDE_PRELOAD_SCOPE, 0L, false);
    }

    public static String translateForLayout(String text) {
        if (text == null || text.isEmpty()) return text;
        if (!isGuideMeAvailable()) return translatePreservingEdgeWhitespace(text, true);

        requestWorldPreload();

        String cachedOrOriginal = translateCacheOnlyPreservingEdgeWhitespace(text);
        if (!cachedOrOriginal.equals(text)) return cachedOrOriginal;

        if (isPreloading()) {
            // The first visible page can be laid out while the global guide
            // preload is still running. Do not freeze that first layout in
            // English: force the current fragment synchronously and only fall
            // back to an idle refresh if the translation engine cannot answer.
            ProjectBabelDebug.info(ProjectBabelDebug.BOOKS, "GuideME layout miss while preload active; forcing text={}", truncate(text, 80));
            String translated = translatePreservingEdgeWhitespace(text, true);
            if (!translated.equals(text)) return translated;

            translatePreservingEdgeWhitespace(text, false);
            requestRefreshAfterIdle("miss de layout GuideME");
            return text;
        }

        ProjectBabelDebug.info(ProjectBabelDebug.BOOKS, "GuideME blocking layout text={}", truncate(text, 80));
        return translatePreservingEdgeWhitespace(text, true);
    }

    private static String translateCacheOnlyPreservingEdgeWhitespace(String text) {
        return translatePreservingEdgeWhitespace(text, null);
    }

    private static String translatePreservingEdgeWhitespace(String text, Boolean blocking) {
        if (text == null || text.isEmpty()) return text;

        int start = 0;
        int end = text.length();
        while (start < end && Character.isWhitespace(text.charAt(start))) start++;
        while (end > start && Character.isWhitespace(text.charAt(end - 1))) end--;

        String prefix = text.substring(0, start);
        String core = text.substring(start, end);
        String suffix = text.substring(end);
        if (core.isBlank()) return text;

        String translated;
        if (blocking == null) {
            translated = TranslationPipeline.translateString(core, TranslationContext.book(false).asCacheOnly());
        } else if (blocking) {
            translated = TranslationPipeline.translateString(core, TranslationContext.book(true));
        } else {
            translated = TranslationPipeline.translateString(core, TranslationContext.book(false));
        }

        if (translated == null || translated.equals(core)) return text;
        return prefix + translated + suffix;
    }

    public static boolean isPreloading() {
        return BookPreloadCoordinator.isPreloading(GuideMeAdapter.ID);
    }

    public static void reset() {
        BookPreloadCoordinator.reset(GuideMeAdapter.ID);
    }

    private static void requestRefreshAfterIdle(String reason) {
        BookPreloadCoordinator.requestRefreshAfterIdle(
            GuideMeAdapter.ID,
            PRELOAD_WAIT_TIMEOUT_MS,
            reason,
            () -> GuideMeReloadHelper.requestRefresh(reason)
        );
    }

    private static boolean runPreload(BookPreloadContext context, String target) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null) return false;

            ResourceManager manager = mc.getResourceManager();
            Map<ResourceLocation, Resource> resources = manager.listResources(
                "",
                GuideMePreloader::isGuideMarkdownResource
            );

            if (resources.isEmpty()) {
                context.markReady();
                ProjectBabelCommon.LOGGER.info(
                    "[projectbabel] GuideME preload: nenhum markdown de guia encontrado."
                );
                return true;
            }

            LinkedHashSet<String> fragments = new LinkedHashSet<>(8192);
            for (Map.Entry<ResourceLocation, Resource> entry : resources.entrySet()) {
                collectMarkdownFragments(entry.getKey(), entry.getValue(), fragments);
            }

            int queued = 0;
            try (TextFilter.ScreenFilterBypass ignored = TextFilter.bypassScreenFilter()) {
                for (String fragment : fragments) {
                    if (!shouldPreload(fragment)) continue;
                    TranslationPipeline.translateString(fragment, TranslationContext.book(false));
                    queued++;
                }
            }

            ProjectBabelDebug.info(
                ProjectBabelDebug.BOOKS,
                "GuideME preload files={} fragments={} queued={}",
                resources.size(),
                fragments.size(),
                queued
            );
            ProjectBabelCommon.LOGGER.info(
                "[projectbabel] GuideME preload: {} textos enfileirados ({} arquivos, alvo {}).",
                queued,
                resources.size(),
                target
            );

            if (queued > 0) {
                boolean complete = TranslationManager.getInstance().waitForIdle(PRELOAD_WAIT_TIMEOUT_MS);
                if (!complete) {
                    ProjectBabelCommon.LOGGER.warn(
                        "[projectbabel] GuideME preload ainda nao terminou apos {}s (fila={}, pendentes={}).",
                        PRELOAD_WAIT_TIMEOUT_MS / 1000L,
                        TranslationManager.getInstance().getQueuedCount(),
                        TranslationManager.getInstance().getPendingCount()
                    );
                    return false;
                }
            }

            if (context.isCurrentGeneration()) {
                context.markReady();
                ProjectBabelCommon.LOGGER.info(
                    "[projectbabel] GuideME preload concluido: cache pronto para alvo {}.",
                    target
                );
                GuideMeReloadHelper.requestRefresh("preload GuideME concluido");
            }
            return true;
        } catch (Exception e) {
            ProjectBabelCommon.LOGGER.warn(
                "[projectbabel] GuideME preload falhou: {}",
                e.getMessage()
            );
            return false;
        }
    }


    private static boolean isGuideMarkdownResource(ResourceLocation id) {
        if (id == null) return false;
        String namespace = id.getNamespace().toLowerCase(java.util.Locale.ROOT);
        String path = id.getPath().toLowerCase(java.util.Locale.ROOT);
        if (!path.endsWith(".md")) return false;

        // AE2 uses assets/<namespace>/ae2guide/... . Other GuideME users commonly
        // keep pages under guide/guides/guideme/guidebook paths. Keep this focused
        // enough to avoid preloading unrelated markdown such as changelogs.
        return path.startsWith("ae2guide/")
            || path.startsWith("guideme/")
            || path.startsWith("guide/")
            || path.startsWith("guides/")
            || path.startsWith("guidebook/")
            || path.contains("/ae2guide/")
            || path.contains("/guideme/")
            || path.contains("/guide/")
            || path.contains("/guides/")
            || path.contains("/guidebook/")
            || namespace.contains("guideme")
            || namespace.contains("guide");
    }

    private static void collectMarkdownFragments(
        ResourceLocation id,
        Resource resource,
        Set<String> fragments
    ) {
        try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(resource.open(), StandardCharsets.UTF_8))) {

            StringBuilder source = new StringBuilder(8192);
            String line;
            while ((line = reader.readLine()) != null) {
                source.append(line).append('\n');
            }

            if (!collectWithGuideMeParser(id, source.toString(), fragments)) {
                collectWithFallbackScanner(source.toString(), fragments);
            }
        } catch (Exception e) {
            ProjectBabelCommon.LOGGER.debug(
                "[projectbabel] GuideME preload ignorou {}: {}",
                id,
                e.getMessage()
            );
        }
    }

    private static boolean collectWithGuideMeParser(
        ResourceLocation id,
        String source,
        Set<String> fragments
    ) {
        return GuideMeAccess.collectWithParser(id, source, fragments);
    }


    private static void collectWithFallbackScanner(String source, Set<String> fragments) {
        boolean codeBlock = false;
        String[] lines = source.split("\\R");
        for (String rawLine : lines) {
            String line = rawLine.strip();
            if (line.startsWith("```") || line.startsWith("~~~")) {
                codeBlock = !codeBlock;
                continue;
            }
            if (codeBlock || line.isBlank()) continue;
            if (line.startsWith("---") || line.startsWith("|---") || line.matches("^\\|?\\s*:?-{3,}.*")) continue;

            line = line.replaceFirst("^#{1,6}\\s+", "");
            line = line.replaceFirst("^>\\s*", "");
            line = line.replaceFirst("^[-*+]\\s+", "");
            line = line.replaceFirst("^\\d+[.)]\\s+", "");
            line = line.replaceAll("!\\[[^]]*]\\([^)]*\\)", "");
            line = line.replaceAll("\\[([^]]+)]\\([^)]*\\)", "$1");
            line = line.replaceAll("`[^`]*`", "");
            line = line.replaceAll("<[^>]+>", " ");
            line = line.replace("*", "").replace("_", "").replace("~", "").strip();

            if (!line.isBlank()) fragments.add(line.strip());
        }
    }

    private static boolean shouldPreload(String text) {
        if (text == null) return false;

        String plain = TextFormatUtils.stripFormatting(text);
        if (plain == null) return false;
        plain = plain.strip();
        if (plain.length() < 3) return false;
        if (!TextFilter.shouldTranslate(plain)) return false;
        if (looksLikeTechnicalToken(plain)) return false;

        return true;
    }

    private static boolean looksLikeTechnicalToken(String text) {
        if (text.indexOf(' ') >= 0) return false;
        if (text.indexOf(':') >= 0 || text.indexOf('/') >= 0 || text.indexOf('\\') >= 0) return true;
        if (text.startsWith("#") || text.startsWith("@")) return true;
        return text.matches("^[a-z0-9_.-]+$") && (text.indexOf('.') >= 0 || text.indexOf('_') >= 0);
    }

    public static boolean isGuideMeAvailable() {
        return GuideMeAccess.isAvailable();
    }


    private static String truncate(String text, int max) {
        if (text == null) return null;
        if (text.length() <= max) return text;
        return text.substring(0, Math.max(0, max - 3)) + "...";
    }

    private static final class GuideMeAdapter implements BookPreloadAdapter {
        private static final String ID = "GuideME";

        @Override
        public String integrationId() {
            return ID;
        }

        @Override
        public BookIdentity resolveIdentity(Object source) {
            return new BookIdentity("guideme", GUIDE_PRELOAD_SCOPE, GUIDE_PRELOAD_SCOPE);
        }

        @Override
        public boolean runPreload(BookPreloadContext context, BookIdentity identity, String targetLanguage, Object source) {
            return GuideMePreloader.runPreload(context, targetLanguage);
        }
    }

}
