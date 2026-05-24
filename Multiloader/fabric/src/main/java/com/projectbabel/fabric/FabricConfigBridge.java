package com.projectbabel.fabric;

import com.projectbabel.fabric.config.AutoTranslateConfig;
import com.projectbabel.platform.BabelConfigView;

/** Fabric-backed mutable config view exposed to common code. */
public final class FabricConfigBridge implements BabelConfigView {
    public static void load() { AutoTranslateConfig.load(); }

    @Override public boolean isEnabled() { return AutoTranslateConfig.isEnabled(); }
    @Override public String getSourceLang() { return AutoTranslateConfig.getSourceLang(); }
    @Override public String getTargetLang() { return AutoTranslateConfig.getTargetLang(); }
    @Override public boolean isFollowClientLanguage() { return AutoTranslateConfig.isFollowClientLanguage(); }
    @Override public boolean isTranslateRenamedItems() { return AutoTranslateConfig.isTranslateRenamedItems(); }
    @Override public boolean isTranslateChat() { return AutoTranslateConfig.isTranslateChat(); }
    @Override public int getCacheSize() { return AutoTranslateConfig.getCacheSize(); }
    @Override public boolean isCachePersist() { return AutoTranslateConfig.isCachePersist(); }
    @Override public int getMinTextLength() { return AutoTranslateConfig.getMinTextLength(); }
    @Override public boolean isFilterNumbers() { return AutoTranslateConfig.isFilterNumbers(); }
    @Override public boolean isFilterCoordinates() { return AutoTranslateConfig.isFilterCoordinates(); }
    @Override public int getRequestTimeoutMs() { return AutoTranslateConfig.getRequestTimeoutMs(); }
    @Override public int getMaxConcurrentRequests() { return AutoTranslateConfig.getMaxConcurrentRequests(); }
    @Override public boolean isTurboMode() { return AutoTranslateConfig.isTurboMode(); }
    @Override public int getFailureThreshold() { return AutoTranslateConfig.getFailureThreshold(); }
    @Override public boolean isShowHudIndicator() { return AutoTranslateConfig.isShowHudIndicator(); }
    @Override public boolean isDebugEnchantments() { return AutoTranslateConfig.isDebugEnchantments(); }
    @Override public String getDebugScope() { return AutoTranslateConfig.getDebugScope(); }
    @Override public boolean isDebugScope(String scope) { return AutoTranslateConfig.isDebugScope(scope); }
    @Override public boolean isUniversalTermsEnabled() { return AutoTranslateConfig.isUniversalTermsEnabled(); }
    @Override public boolean isUniversalTermsRemote() { return AutoTranslateConfig.isUniversalTermsRemote(); }
    @Override public String getUniversalTermsRemoteUrl() { return AutoTranslateConfig.getUniversalTermsRemoteUrl(); }
    @Override public String getUniversalTermsLocalPath() { return AutoTranslateConfig.getUniversalTermsLocalPath(); }

    @Override public void setEnabled(boolean value) { AutoTranslateConfig.setEnabled(value); }
    @Override public void setSourceLang(String value) { AutoTranslateConfig.setSourceLang(value); }
    @Override public void setTargetLang(String value) { AutoTranslateConfig.setTargetLang(value); }
    @Override public void setFollowClientLanguage(boolean value) { AutoTranslateConfig.setFollowClientLanguage(value); }
    @Override public void setTranslateRenamedItems(boolean value) { AutoTranslateConfig.setTranslateRenamedItems(value); }
    @Override public void setTranslateChat(boolean value) { AutoTranslateConfig.setTranslateChat(value); }
    @Override public void setTurboMode(boolean value) { AutoTranslateConfig.setTurboMode(value); }
    @Override public void setShowHudIndicator(boolean value) { AutoTranslateConfig.setShowHudIndicator(value); }
    @Override public void setDebugScope(String value) { AutoTranslateConfig.setDebugScope(value); }
    @Override public String cycleDebugScope() { return AutoTranslateConfig.cycleDebugScope(); }
    @Override public void setUniversalTermsEnabled(boolean value) { AutoTranslateConfig.setUniversalTermsEnabled(value); }
    @Override public void setUniversalTermsRemote(boolean value) { AutoTranslateConfig.setUniversalTermsRemote(value); }
    @Override public void setUniversalTermsRemoteUrl(String value) { AutoTranslateConfig.setUniversalTermsRemoteUrl(value); }
    @Override public void setUniversalTermsLocalPath(String value) { AutoTranslateConfig.setUniversalTermsLocalPath(value); }
}
