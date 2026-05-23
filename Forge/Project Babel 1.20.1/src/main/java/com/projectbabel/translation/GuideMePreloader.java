package com.projectbabel.translation;

import com.projectbabel.ProjectBabelMod;
import com.projectbabel.debug.ProjectBabelDebug;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraftforge.fml.ModList;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Preloads GuideME/AE2 Guide markdown while the client is entering a world.
 *
 * GuideME lays out markdown text once, so the actual render hook cannot safely
 * translate late without breaking wrapping. This class warms the same literal
 * text fragments that GuideME will later pass into LytFlowText#setText.
 */
public final class GuideMePreloader {

    private static final long PRELOAD_WAIT_TIMEOUT_MS = 120_000L;
    private static final String GUIDE_PREFIX = "ae2guide";

    private static final Set<String> READY_TARGETS = ConcurrentHashMap.newKeySet();
    private static final ConcurrentHashMap<String, CompletableFuture<Void>> RUNNING =
        new ConcurrentHashMap<>();
    private static final AtomicLong GENERATION = new AtomicLong(0L);
    private static final AtomicBoolean REFRESH_AFTER_IDLE_SCHEDULED = new AtomicBoolean(false);

    private GuideMePreloader() {}

    public static void requestWorldPreload() {
        if (!isGuideMeAvailable()) return;
        if (!LanguageDetector.shouldModBeActive()) return;

        String target = LanguageDetector.getTargetLanguageForApi();
        String preloadKey = target + ':' + GUIDE_PREFIX;
        if (READY_TARGETS.contains(preloadKey) || RUNNING.containsKey(preloadKey)) return;

        long generation = GENERATION.get();
        CompletableFuture<Void> future = CompletableFuture.runAsync(
            () -> PreloadAcceleration.run(() -> runPreload(preloadKey, target, generation)),
            TranslationExecutors.preload()
        );

        CompletableFuture<Void> existing = RUNNING.putIfAbsent(preloadKey, future);
        if (existing != null) {
            future.cancel(false);
            return;
        }

        future.whenComplete((ignored, error) -> {
            RUNNING.remove(preloadKey, future);
            if (error != null) {
                ProjectBabelMod.LOGGER.warn(
                    "[projectbabel] GuideME preload falhou: {}",
                    error.getMessage()
                );
            }
        });
    }

    public static String translateForLayout(String text) {
        if (text == null || text.isEmpty()) return text;
        if (!isGuideMeAvailable()) return TranslationPipeline.translateStringBlocking(text);

        requestWorldPreload();

        String cachedOrOriginal = TranslationPipeline.translateStringCacheOnly(text);
        if (!cachedOrOriginal.equals(text)) return cachedOrOriginal;

        if (isPreloading()) {
            // LytFlowText already calculated this page with the original text.
            // Keep the render thread non-blocking, enqueue a real translation for
            // fragments not found by the markdown preload, then rebuild the guide
            // once the queue is idle.
            TranslationPipeline.translateString(text);
            requestRefreshAfterIdle("miss de layout GuideME");
            return text;
        }

        ProjectBabelDebug.info(ProjectBabelDebug.BOOKS, "GuideME blocking layout text={}", truncate(text, 80));
        return TranslationPipeline.translateStringBlocking(text);
    }

    public static boolean isPreloading() {
        return !RUNNING.isEmpty();
    }

    public static void reset() {
        GENERATION.incrementAndGet();
        READY_TARGETS.clear();
        RUNNING.clear();
        REFRESH_AFTER_IDLE_SCHEDULED.set(false);
    }

    private static void requestRefreshAfterIdle(String reason) {
        if (!REFRESH_AFTER_IDLE_SCHEDULED.compareAndSet(false, true)) return;

        CompletableFuture.runAsync(() -> {
            try {
                TranslationManager.getInstance().waitForIdle(PRELOAD_WAIT_TIMEOUT_MS);
                GuideMeReloadHelper.requestRefresh(reason);
            } finally {
                REFRESH_AFTER_IDLE_SCHEDULED.set(false);
            }
        }, TranslationExecutors.preload());
    }

    private static void runPreload(String preloadKey, String target, long generation) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null) return;

            ResourceManager manager = mc.getResourceManager();
            Map<ResourceLocation, Resource> resources = manager.listResources(
                GUIDE_PREFIX,
                path -> path.getPath().endsWith(".md")
            );

            if (resources.isEmpty()) {
                READY_TARGETS.add(preloadKey);
                ProjectBabelMod.LOGGER.info(
                    "[projectbabel] GuideME preload: nenhum arquivo ae2guide encontrado."
                );
                return;
            }

            LinkedHashSet<String> fragments = new LinkedHashSet<>(8192);
            for (Map.Entry<ResourceLocation, Resource> entry : resources.entrySet()) {
                collectMarkdownFragments(entry.getKey(), entry.getValue(), fragments);
            }

            int queued = 0;
            try (TextFilter.ScreenFilterBypass ignored = TextFilter.bypassScreenFilter()) {
                for (String fragment : fragments) {
                    if (!shouldPreload(fragment)) continue;
                    TranslationPipeline.translateString(fragment);
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
            ProjectBabelMod.LOGGER.info(
                "[projectbabel] GuideME/AE2 Guide preload: {} textos enfileirados ({} arquivos, alvo {}).",
                queued,
                resources.size(),
                target
            );

            if (queued > 0) {
                boolean complete = TranslationManager.getInstance().waitForIdle(PRELOAD_WAIT_TIMEOUT_MS);
                if (!complete) {
                    ProjectBabelMod.LOGGER.warn(
                        "[projectbabel] GuideME preload ainda nao terminou apos {}s (fila={}, pendentes={}).",
                        PRELOAD_WAIT_TIMEOUT_MS / 1000L,
                        TranslationManager.getInstance().getQueuedCount(),
                        TranslationManager.getInstance().getPendingCount()
                    );
                    return;
                }
            }

            if (GENERATION.get() == generation) {
                READY_TARGETS.add(preloadKey);
                ProjectBabelMod.LOGGER.info(
                    "[projectbabel] GuideME/AE2 Guide preload concluido: cache pronto para alvo {}.",
                    target
                );
                GuideMeReloadHelper.requestRefresh("preload GuideME concluido");
            }
        } catch (Exception e) {
            ProjectBabelMod.LOGGER.warn(
                "[projectbabel] GuideME preload falhou: {}",
                e.getMessage()
            );
        }
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
            ProjectBabelMod.LOGGER.debug(
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
        try {
            Class<?> compilerClass = Class.forName("guideme.compiler.PageCompiler");
            Method parse = compilerClass.getMethod(
                "parse",
                String.class,
                String.class,
                ResourceLocation.class,
                String.class
            );

            Object parsed = parse.invoke(null, "projectbabel", "en_us", id, source);
            Object root = parsed.getClass().getMethod("getAstRoot").invoke(parsed);
            collectAstText(root, fragments, Collections.newSetFromMap(new IdentityHashMap<>()));
            return true;
        } catch (Exception e) {
            ProjectBabelMod.LOGGER.debug(
                "[projectbabel] GuideME parser indisponivel para {}: {}",
                id,
                e.getMessage()
            );
            return false;
        }
    }

    private static void collectAstText(Object node, Set<String> fragments, Set<Object> seen) {
        if (node == null || !seen.add(node)) return;

        String className = node.getClass().getName();
        if ("guideme.libs.mdast.model.MdAstText".equals(className)
            || "guideme.libs.mdast.model.MdAstInlineCode".equals(className)) {
            String value = readLiteralValue(node);
            if (value != null) fragments.add(value);
        }

        Iterable<?> children = readChildren(node);
        if (children != null) {
            for (Object child : children) {
                collectAstText(child, fragments, seen);
            }
        }
    }

    private static String readLiteralValue(Object node) {
        try {
            Method value = node.getClass().getMethod("value");
            Object result = value.invoke(node);
            return result instanceof String text ? text : null;
        } catch (Exception ignored) {
        }

        Class<?> type = node.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField("value");
                field.setAccessible(true);
                Object result = field.get(node);
                return result instanceof String text ? text : null;
            } catch (ReflectiveOperationException ignored) {
                type = type.getSuperclass();
            }
        }
        return null;
    }

    private static Iterable<?> readChildren(Object node) {
        try {
            Method children = node.getClass().getMethod("children");
            Object result = children.invoke(node);
            return result instanceof Iterable<?> iterable ? iterable : null;
        } catch (Exception ignored) {
            return null;
        }
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

            if (!line.isBlank()) fragments.add(line);
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

    private static boolean isGuideMeAvailable() {
        try {
            return ModList.get().isLoaded("guideme");
        } catch (Exception ignored) {
            return false;
        }
    }

    private static String truncate(String text, int max) {
        if (text == null) return null;
        if (text.length() <= max) return text;
        return text.substring(0, Math.max(0, max - 3)) + "...";
    }
}
