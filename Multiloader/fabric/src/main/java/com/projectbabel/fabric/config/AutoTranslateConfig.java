package com.projectbabel.fabric.config;

import com.projectbabel.ProjectBabelCommon;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;

public class AutoTranslateConfig {

    private static final Properties VALUES = new Properties();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("projectbabel-client.properties");
    private static boolean loaded = false;

    static {
        setDefaults();
    }

    private static void setDefaults() {
        VALUES.setProperty("translation.enabled", "true");
        VALUES.setProperty("translation.sourceLang", "en");
        VALUES.setProperty("translation.targetLang", "pt");
        VALUES.setProperty("translation.followClientLanguage", "true");
        VALUES.setProperty("translation.translateRenamedItems", "false");
        VALUES.setProperty("translation.translateChat", "true");
        VALUES.setProperty("cache.maxSize", "500000");
        VALUES.setProperty("cache.persist", "true");
        VALUES.setProperty("filters.minLength", "1");
        VALUES.setProperty("filters.filterNumbers", "true");
        VALUES.setProperty("filters.filterCoordinates", "true");
        VALUES.setProperty("performance.requestTimeoutMs", "4000");
        VALUES.setProperty("performance.maxConcurrentRequests", "128");
        VALUES.setProperty("performance.turboMode", "true");
        VALUES.setProperty("performance.failureThreshold", "5");
        VALUES.setProperty("display.showHudIndicator", "false");
        VALUES.setProperty("universal_terms.enabled", "true");
        VALUES.setProperty("universal_terms.useRemote", "true");
        VALUES.setProperty("universal_terms.remoteUrl", "https://raw.githubusercontent.com/WallaceFvck/Project-Babel/main/docs/universal_terms.json");
        VALUES.setProperty("universal_terms.localPath", "projectbabel_universal_terms.json");
        VALUES.setProperty("debug.debugEnchantments", "false");
        VALUES.setProperty("debug.debugScope", "off");
    }

    public static synchronized void load() {
        if (loaded) return;
        loaded = true;
        try {
            if (Files.exists(CONFIG_PATH)) {
                try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
                    Properties fileValues = new Properties();
                    fileValues.load(reader);
                    fileValues.forEach((key, value) -> {
                        if (key != null && value != null) VALUES.setProperty(String.valueOf(key), String.valueOf(value));
                    });
                }
            }
            sanitize();
            save();
        } catch (Exception error) {
            ProjectBabelCommon.LOGGER.warn("[projectbabel] Nao foi possivel carregar a config Fabric: {}", error.toString());
        }
    }

    private static void sanitize() {
        migrateAliases();
        setRaw("translation.sourceLang", toApiLanguage(getRaw("translation.sourceLang", "en"), "en"));
        setRaw("translation.targetLang", toApiLanguage(getRaw("translation.targetLang", "pt"), "pt"));
        setRaw("cache.maxSize", String.valueOf(clamp(getInt("cache.maxSize", 500000), 50000, 1000000)));
        setRaw("filters.minLength", String.valueOf(clamp(getInt("filters.minLength", 1), 1, 20)));
        setRaw("performance.requestTimeoutMs", String.valueOf(clamp(getInt("performance.requestTimeoutMs", 4000), 500, 20000)));
        setRaw("performance.maxConcurrentRequests", String.valueOf(clamp(getInt("performance.maxConcurrentRequests", 128), 1, 2048)));
        setRaw("performance.failureThreshold", String.valueOf(clamp(getInt("performance.failureThreshold", 5), 1, 10)));
        setRaw("universal_terms.localPath", normalizeLocalDictionaryPath(getRaw("universal_terms.localPath", "projectbabel_universal_terms.json")));
        if (!isValidHttpUrl(getRaw("universal_terms.remoteUrl", ""))) {
            setRaw("universal_terms.remoteUrl", "https://raw.githubusercontent.com/WallaceFvck/Project-Babel/main/docs/universal_terms.json");
        }
        setRaw("debug.debugScope", normalizeDebugScope(getRaw("debug.debugScope", "off")));
    }

    public static boolean isEnabled()               { return getBool("translation.enabled", true); }
    public static void setEnabled(boolean v)        { setBool("translation.enabled", v); save(); }
    public static String getSourceLang()            { return toApiLanguage(getRaw("translation.sourceLang", "en"), "en"); }
    public static void setSourceLang(String v)      { setRaw("translation.sourceLang", toApiLanguage(v, "en")); save(); }
    public static String getTargetLang()            { return toApiLanguage(getRaw("translation.targetLang", "pt"), "pt"); }
    public static void setTargetLang(String v)      { setRaw("translation.targetLang", toApiLanguage(v, "pt")); save(); }
    public static boolean isFollowClientLanguage()  { return getBool("translation.followClientLanguage", true); }
    public static void setFollowClientLanguage(boolean v) { setBool("translation.followClientLanguage", v); save(); }
    public static boolean isTranslateRenamedItems() { return getBool("translation.translateRenamedItems", false); }
    public static void setTranslateRenamedItems(boolean v) { setBool("translation.translateRenamedItems", v); save(); }
    public static boolean isTranslateChat()         { return getBool("translation.translateChat", true); }
    public static void setTranslateChat(boolean v)  { setBool("translation.translateChat", v); save(); }
    public static int getCacheSize()                { return clamp(getInt("cache.maxSize", 500000), 50000, 1000000); }
    public static boolean isCachePersist()          { return getBool("cache.persist", true); }
    public static int getMinTextLength()            { return clamp(getInt("filters.minLength", 1), 1, 20); }
    public static boolean isFilterNumbers()         { return getBool("filters.filterNumbers", true); }
    public static boolean isFilterCoordinates()     { return getBool("filters.filterCoordinates", true); }
    public static int getRequestTimeoutMs()         { return clamp(getInt("performance.requestTimeoutMs", 4000), 500, 20000); }
    public static int getMaxConcurrentRequests()    { return clamp(getInt("performance.maxConcurrentRequests", 128), 1, 2048); }
    public static boolean isTurboMode()             { return getBool("performance.turboMode", true); }
    public static void setTurboMode(boolean v)      { setBool("performance.turboMode", v); save(); }
    public static int getFailureThreshold()         { return clamp(getInt("performance.failureThreshold", 5), 1, 10); }
    public static boolean isShowHudIndicator()      { return getBool("display.showHudIndicator", false); }
    public static void setShowHudIndicator(boolean v) { setBool("display.showHudIndicator", v); save(); }
    public static boolean isDebugEnchantments()     { return getBool("debug.debugEnchantments", false) || isDebugScope("tooltip"); }
    public static String getDebugScope()            { return normalizeDebugScope(getRaw("debug.debugScope", "off")); }
    public static void setDebugScope(String v)      { setRaw("debug.debugScope", normalizeDebugScope(v)); save(); }
    public static boolean isUniversalTermsEnabled() { return getBool("universal_terms.enabled", true); }
    public static void setUniversalTermsEnabled(boolean v) { setBool("universal_terms.enabled", v); save(); }
    public static boolean isUniversalTermsRemote()  { return getBool("universal_terms.useRemote", true); }
    public static void setUniversalTermsRemote(boolean v) { setBool("universal_terms.useRemote", v); save(); }
    public static String getUniversalTermsRemoteUrl() { return getRaw("universal_terms.remoteUrl", "https://raw.githubusercontent.com/WallaceFvck/Project-Babel/main/docs/universal_terms.json"); }
    public static void setUniversalTermsRemoteUrl(String v) {
        if (v != null && isValidHttpUrl(v)) setRaw("universal_terms.remoteUrl", v.trim());
        save();
    }
    public static String getUniversalTermsLocalPath() { return getRaw("universal_terms.localPath", "projectbabel_universal_terms.json"); }
    public static void setUniversalTermsLocalPath(String v) {
        setRaw("universal_terms.localPath", normalizeLocalDictionaryPath(v));
        save();
    }
    public static boolean isDebugScope(String scope) {
        String current = getDebugScope();
        if ("all".equals(current)) return true;
        return current.equals(normalizeDebugScope(scope));
    }

    public static String cycleDebugScope() {
        String current = getDebugScope();
        String next = switch (current) {
            case "off" -> "tooltip";
            case "tooltip" -> "quests";
            case "quests" -> "books";
            case "books" -> "ponder";
            case "ponder" -> "all";
            default -> "off";
        };
        setDebugScope(next);
        return next;
    }

    private static void migrateAliases() {
        migrateAlias("universal_terms.local_path", "universal_terms.localPath");
        migrateAlias("universal_terms.localpath", "universal_terms.localPath");
        migrateAlias("universalTerms.localPath", "universal_terms.localPath");
        migrateAlias("universal_terms.remote_url", "universal_terms.remoteUrl");
        migrateAlias("universal_terms.remoteurl", "universal_terms.remoteUrl");
        migrateAlias("universalTerms.remoteUrl", "universal_terms.remoteUrl");
        migrateAlias("universal_terms.use_remote", "universal_terms.useRemote");
        migrateAlias("universalTerms.useRemote", "universal_terms.useRemote");
    }

    private static void migrateAlias(String alias, String canonical) {
        String value = VALUES.getProperty(alias);
        if (value == null || value.isBlank()) return;
        if (VALUES.getProperty(canonical) == null || VALUES.getProperty(canonical, "").isBlank()) {
            VALUES.setProperty(canonical, value);
        }
        VALUES.remove(alias);
    }

    private static boolean isValidHttpUrl(String value) {
        if (value == null) return false;
        String trimmed = value.trim().toLowerCase(Locale.ROOT);
        return trimmed.startsWith("https://") || trimmed.startsWith("http://");
    }

    private static String normalizeLocalDictionaryPath(String value) {
        if (value == null || value.isBlank()) return "projectbabel_universal_terms.json";
        String normalized = value.trim();
        if (normalized.length() > 512 || normalized.indexOf('\0') >= 0 || normalized.contains("\n") || normalized.contains("\r")) {
            return "projectbabel_universal_terms.json";
        }
        return normalized;
    }

    private static String normalizeDebugScope(String value) {
        if (value == null || value.isBlank()) return "off";
        String normalized = value.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        return switch (normalized) {
            case "tooltip", "tooltips", "ench", "enchantments" -> "tooltip";
            case "quest", "quests", "ftb", "ftbquests" -> "quests";
            case "book", "books", "patchouli", "guideme", "modonomicon" -> "books";
            case "ponder", "create" -> "ponder";
            case "all", "on", "true" -> "all";
            default -> "off";
        };
    }

    private static String normalizeLanguage(String value, String fallback) {
        if (value == null || value.isBlank()) return fallback;
        String normalized = value.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        return normalized.matches("[a-z]{2,3}(_[a-z]{2})?") ? normalized : fallback;
    }

    private static String toApiLanguage(String value, String fallback) {
        String normalized = normalizeLanguage(value, fallback);
        int separator = normalized.indexOf('_');
        return separator > 0 ? normalized.substring(0, separator) : normalized;
    }

    private static boolean getBool(String key, boolean fallback) {
        load();
        return Boolean.parseBoolean(getRaw(key, String.valueOf(fallback)));
    }

    private static void setBool(String key, boolean value) {
        setRaw(key, String.valueOf(value));
    }

    private static int getInt(String key, int fallback) {
        load();
        try {
            return Integer.parseInt(getRaw(key, String.valueOf(fallback)).trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String getRaw(String key, String fallback) {
        return VALUES.getProperty(key, fallback);
    }

    private static void setRaw(String key, String value) {
        VALUES.setProperty(key, value == null ? "" : value);
    }

    private static synchronized void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
                VALUES.store(writer, "Project Babel Fabric client config");
            }
        } catch (IOException ignored) {
        }
    }
}
