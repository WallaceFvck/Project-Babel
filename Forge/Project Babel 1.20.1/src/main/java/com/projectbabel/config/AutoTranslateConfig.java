package com.projectbabel.config;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.Locale;

public class AutoTranslateConfig {

    public static final ForgeConfigSpec SPEC;

    private static final ForgeConfigSpec.BooleanValue ENABLED;
    private static final ForgeConfigSpec.ConfigValue<String> SOURCE_LANG;
    private static final ForgeConfigSpec.ConfigValue<String> TARGET_LANG;
    private static final ForgeConfigSpec.BooleanValue FOLLOW_CLIENT_LANGUAGE;
    private static final ForgeConfigSpec.BooleanValue TRANSLATE_RENAMED_ITEMS;
    private static final ForgeConfigSpec.BooleanValue TRANSLATE_CHAT;
    private static final ForgeConfigSpec.IntValue CACHE_SIZE;
    private static final ForgeConfigSpec.BooleanValue CACHE_PERSIST;
    private static final ForgeConfigSpec.IntValue MIN_TEXT_LENGTH;
    private static final ForgeConfigSpec.BooleanValue FILTER_NUMBERS;
    private static final ForgeConfigSpec.BooleanValue FILTER_COORDINATES;
    private static final ForgeConfigSpec.IntValue REQUEST_TIMEOUT_MS;
    private static final ForgeConfigSpec.IntValue MAX_CONCURRENT_REQUESTS;
    private static final ForgeConfigSpec.BooleanValue TURBO_MODE;
    private static final ForgeConfigSpec.IntValue FAILURE_THRESHOLD;
    private static final ForgeConfigSpec.BooleanValue SHOW_HUD_INDICATOR;
    private static final ForgeConfigSpec.BooleanValue DEBUG_ENCHANTMENTS;
    private static final ForgeConfigSpec.ConfigValue<String> DEBUG_SCOPE;
    private static final ForgeConfigSpec.BooleanValue UNIVERSAL_TERMS_ENABLED;
    private static final ForgeConfigSpec.BooleanValue UNIVERSAL_TERMS_REMOTE;
    private static final ForgeConfigSpec.ConfigValue<String> UNIVERSAL_TERMS_REMOTE_URL;
    private static final ForgeConfigSpec.ConfigValue<String> UNIVERSAL_TERMS_LOCAL_PATH;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.comment("projectbabel settings").push("translation");

        ENABLED = builder
            .comment("Enable automatic translation. Can also be toggled in the in-game cache screen.")
            .define("enabled", true);

        SOURCE_LANG = builder
            .comment("Source language used when language detection cannot decide. ISO 639-1 code, e.g. en, pt, es, fr, de, ja, zh.")
            .define("sourceLang", "en", AutoTranslateConfig::isValidLanguageCode);

        TARGET_LANG = builder
            .comment("Target language used by translation APIs. ISO 639-1 code, e.g. pt, en, es, fr, de, ja, zh.")
            .define("targetLang", "pt", AutoTranslateConfig::isValidLanguageCode);

        FOLLOW_CLIENT_LANGUAGE = builder
            .comment("When true, the Minecraft client language is used as target. When false, targetLang is used.")
            .define("followClientLanguage", true);

        TRANSLATE_RENAMED_ITEMS = builder
            .comment("When false, item names changed by an anvil/custom name are never sent to translation. Original item names are still translated.")
            .define("translateRenamedItems", false);

        TRANSLATE_CHAT = builder
            .comment("Translate received chat messages and mod chat panels.")
            .define("translateChat", true);

        builder.pop().push("cache");

        CACHE_SIZE = builder
            .comment("Maximum number of in-memory cache entries.")
            .defineInRange("maxSize", 500000, 50000, 1000000);

        CACHE_PERSIST = builder
            .comment("Persist cache to disk between sessions.")
            .define("persist", true);

        builder.pop().push("filters");

        MIN_TEXT_LENGTH = builder
            .comment("Texts shorter than this are ignored.")
            .defineInRange("minLength", 1, 1, 20);

        FILTER_NUMBERS = builder
            .comment("Ignore purely numeric strings.")
            .define("filterNumbers", true);

        FILTER_COORDINATES = builder
            .comment("Ignore strings that look like coordinates.")
            .define("filterCoordinates", true);

        builder.pop().push("performance");

        REQUEST_TIMEOUT_MS = builder
            .comment("Timeout in milliseconds for online translation APIs.")
            .defineInRange("requestTimeoutMs", 4000, 500, 20000);

        MAX_CONCURRENT_REQUESTS = builder
            .comment("Maximum number of concurrent translation requests.")
            .defineInRange("maxConcurrentRequests", 2048, 1, 2048);

        TURBO_MODE = builder
            .comment("Turbo mode uses aggressive translation concurrency. Disable to use conservative worker limits.")
            .define("turboMode", true);

        FAILURE_THRESHOLD = builder
            .comment("Consecutive failures before switching to fallback engine.")
            .defineInRange("failureThreshold", 5, 1, 10);

        builder.pop().push("display");

        SHOW_HUD_INDICATOR = builder
            .comment("Show translation status in the HUD.")
            .define("showHudIndicator", false);

        builder.pop().push("universal_terms");

        UNIVERSAL_TERMS_ENABLED = builder
            .comment("Enable Project Babel fixed-term glossary. It is checked before the online translator and avoids API calls for known universal terms.")
            .define("enabled", false);

        UNIVERSAL_TERMS_REMOTE = builder
            .comment("When true, download the glossary from remoteUrl. When false, read localPath from this computer.")
            .define("useRemote", true);

        UNIVERSAL_TERMS_REMOTE_URL = builder
            .comment("Remote JSON dictionary URL. Default points to a multilingual English-source glossary on Project Babel GitHub raw content.")
            .define("remoteUrl", "https://raw.githubusercontent.com/WallaceFvck/Project-Babel/main/docs/universal_terms.json", AutoTranslateConfig::isValidHttpUrl);

        UNIVERSAL_TERMS_LOCAL_PATH = builder
            .comment("Local JSON dictionary path. Relative paths are resolved from the Minecraft game directory. Blank uses projectbabel_universal_terms.json. The file may contain targets for any language, always from English.")
            .define("localPath", "projectbabel_universal_terms.json", AutoTranslateConfig::isValidLocalDictionaryPath);

        builder.pop().push("debug");

        DEBUG_ENCHANTMENTS = builder
            .comment("Log detailed diagnostics for enchantment tooltip/description translation. Limited per world to avoid spam.")
            .define("debugEnchantments", false);

        DEBUG_SCOPE = builder
            .comment("Selective debug scope shown in the in-game cache screen. Values: off, tooltip, quests, books, ponder, all.")
            .define("debugScope", "off", AutoTranslateConfig::isValidDebugScope);

        builder.pop();

        SPEC = builder.build();
    }

    public static boolean isEnabled()               { return ENABLED.get(); }
    public static void setEnabled(boolean v)        { ENABLED.set(v); save(); }
    public static String getSourceLang()            { return toApiLanguage(SOURCE_LANG.get(), "en"); }
    public static void setSourceLang(String v)      { SOURCE_LANG.set(toApiLanguage(v, "en")); save(); }
    public static String getTargetLang()            { return toApiLanguage(TARGET_LANG.get(), "pt"); }
    public static void setTargetLang(String v)      { TARGET_LANG.set(toApiLanguage(v, "pt")); save(); }
    public static boolean isFollowClientLanguage()  { return FOLLOW_CLIENT_LANGUAGE.get(); }
    public static void setFollowClientLanguage(boolean v) { FOLLOW_CLIENT_LANGUAGE.set(v); save(); }
    public static boolean isTranslateRenamedItems() { return TRANSLATE_RENAMED_ITEMS.get(); }
    public static void setTranslateRenamedItems(boolean v) { TRANSLATE_RENAMED_ITEMS.set(v); save(); }
    public static boolean isTranslateChat()         { return TRANSLATE_CHAT.get(); }
    public static void setTranslateChat(boolean v)  { TRANSLATE_CHAT.set(v); save(); }
    public static int getCacheSize()                { return CACHE_SIZE.get(); }
    public static boolean isCachePersist()          { return CACHE_PERSIST.get(); }
    public static int getMinTextLength()            { return MIN_TEXT_LENGTH.get(); }
    public static boolean isFilterNumbers()         { return FILTER_NUMBERS.get(); }
    public static boolean isFilterCoordinates()     { return FILTER_COORDINATES.get(); }
    public static int getRequestTimeoutMs()         { return REQUEST_TIMEOUT_MS.get(); }
    public static int getMaxConcurrentRequests()    { return MAX_CONCURRENT_REQUESTS.get(); }
    public static boolean isTurboMode()             { return TURBO_MODE.get(); }
    public static void setTurboMode(boolean v)      { TURBO_MODE.set(v); save(); }
    public static int getFailureThreshold()         { return FAILURE_THRESHOLD.get(); }
    public static boolean isShowHudIndicator()      { return SHOW_HUD_INDICATOR.get(); }
    public static void setShowHudIndicator(boolean v) { SHOW_HUD_INDICATOR.set(v); save(); }
    public static boolean isDebugEnchantments()     { return DEBUG_ENCHANTMENTS.get() || isDebugScope("tooltip"); }
    public static String getDebugScope()            { return normalizeDebugScope(DEBUG_SCOPE.get()); }
    public static void setDebugScope(String v)      { DEBUG_SCOPE.set(normalizeDebugScope(v)); save(); }
    public static boolean isUniversalTermsEnabled() { return UNIVERSAL_TERMS_ENABLED.get(); }
    public static void setUniversalTermsEnabled(boolean v) { UNIVERSAL_TERMS_ENABLED.set(v); save(); }
    public static boolean isUniversalTermsRemote()  { return UNIVERSAL_TERMS_REMOTE.get(); }
    public static void setUniversalTermsRemote(boolean v) { UNIVERSAL_TERMS_REMOTE.set(v); save(); }
    public static String getUniversalTermsRemoteUrl() { return UNIVERSAL_TERMS_REMOTE_URL.get(); }
    public static void setUniversalTermsRemoteUrl(String v) {
        if (v != null && isValidHttpUrl(v)) UNIVERSAL_TERMS_REMOTE_URL.set(v.trim());
        save();
    }
    public static String getUniversalTermsLocalPath() { return UNIVERSAL_TERMS_LOCAL_PATH.get(); }
    public static void setUniversalTermsLocalPath(String v) {
        UNIVERSAL_TERMS_LOCAL_PATH.set(normalizeLocalDictionaryPath(v));
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

    private static boolean isValidLanguageCode(Object value) {
        if (!(value instanceof String s)) return false;
        String normalized = normalizeLanguage(s, "");
        return normalized.length() >= 2 && normalized.length() <= 8 && normalized.matches("[a-z]{2,3}(_[a-z]{2})?");
    }

    private static boolean isValidHttpUrl(Object value) {
        if (!(value instanceof String s)) return false;
        String trimmed = s.trim().toLowerCase(Locale.ROOT);
        return trimmed.startsWith("https://") || trimmed.startsWith("http://");
    }

    private static boolean isValidLocalDictionaryPath(Object value) {
        if (!(value instanceof String s)) return false;
        String normalized = normalizeLocalDictionaryPath(s);
        return normalized.length() <= 512
            && normalized.indexOf('\0') < 0
            && !normalized.contains("\n")
            && !normalized.contains("\r");
    }

    private static String normalizeLocalDictionaryPath(String value) {
        if (value == null || value.isBlank()) return "projectbabel_universal_terms.json";
        return value.trim();
    }

    private static boolean isValidDebugScope(Object value) {
        if (!(value instanceof String s)) return false;
        return switch (normalizeDebugScope(s)) {
            case "off", "tooltip", "quests", "books", "ponder", "all" -> true;
            default -> false;
        };
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
        return value.trim().toLowerCase(Locale.ROOT).replace('-', '_');
    }

    private static String toApiLanguage(String value, String fallback) {
        String normalized = normalizeLanguage(value, fallback);
        int separator = normalized.indexOf('_');
        return separator > 0 ? normalized.substring(0, separator) : normalized;
    }

    private static void save() {
        try {
            SPEC.save();
        } catch (Exception ignored) {
        }
    }
}
