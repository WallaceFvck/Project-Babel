package com.projectbabel.platform;

/**
 * Platform-neutral config contract consumed by common code.
 *
 * Platform modules must adapt ForgeConfigSpec, AutoConfig, ClothConfig or JSON
 * config to this interface instead of exposing loader-specific classes. Mutating methods are used by shared client UI only.
 */
public interface BabelConfigView {
    boolean isEnabled();

    String getSourceLang();

    String getTargetLang();

    boolean isFollowClientLanguage();

    boolean isTranslateRenamedItems();

    boolean isTranslateChat();

    int getCacheSize();

    boolean isCachePersist();

    int getMinTextLength();

    boolean isFilterNumbers();

    boolean isFilterCoordinates();

    int getRequestTimeoutMs();

    int getMaxConcurrentRequests();

    boolean isTurboMode();

    int getFailureThreshold();

    boolean isShowHudIndicator();

    boolean isDebugEnchantments();

    String getDebugScope();

    boolean isDebugScope(String scope);

    boolean isUniversalTermsEnabled();

    boolean isUniversalTermsRemote();

    String getUniversalTermsRemoteUrl();

    String getUniversalTermsLocalPath();

    default void setEnabled(boolean value) { throw new UnsupportedOperationException("Config is read-only"); }

    default void setSourceLang(String value) { throw new UnsupportedOperationException("Config is read-only"); }

    default void setTargetLang(String value) { throw new UnsupportedOperationException("Config is read-only"); }

    default void setFollowClientLanguage(boolean value) { throw new UnsupportedOperationException("Config is read-only"); }

    default void setTranslateRenamedItems(boolean value) { throw new UnsupportedOperationException("Config is read-only"); }

    default void setTranslateChat(boolean value) { throw new UnsupportedOperationException("Config is read-only"); }

    default void setTurboMode(boolean value) { throw new UnsupportedOperationException("Config is read-only"); }

    default void setShowHudIndicator(boolean value) { throw new UnsupportedOperationException("Config is read-only"); }

    default void setDebugScope(String value) { throw new UnsupportedOperationException("Config is read-only"); }

    default String cycleDebugScope() {
        throw new UnsupportedOperationException("Config is read-only");
    }

    default void setUniversalTermsEnabled(boolean value) { throw new UnsupportedOperationException("Config is read-only"); }

    default void setUniversalTermsRemote(boolean value) { throw new UnsupportedOperationException("Config is read-only"); }

    default void setUniversalTermsRemoteUrl(String value) { throw new UnsupportedOperationException("Config is read-only"); }

    default void setUniversalTermsLocalPath(String value) { throw new UnsupportedOperationException("Config is read-only"); }

    default String sourceLanguage() {
        return getSourceLang();
    }

    default String targetLanguage() {
        return getTargetLang();
    }

    default boolean turboMode() {
        return isTurboMode();
    }
}
