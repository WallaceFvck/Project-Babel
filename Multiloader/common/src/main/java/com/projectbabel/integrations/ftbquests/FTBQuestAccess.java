package com.projectbabel.integrations.ftbquests;

import com.projectbabel.integrations.access.ReflectionAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;

/** Semantic reflection boundary for FTB Quests. */
public final class FTBQuestAccess {

    private static final String CLIENT_QUEST_FILE = "dev.ftb.mods.ftbquests.client.ClientQuestFile";
    private static final String TEXT_UTILS = "dev.ftb.mods.ftbquests.util.TextUtils";

    private FTBQuestAccess() {}

    public static Object currentClientQuestFile() {
        Class<?> type = ReflectionAccess.classForName(CLIENT_QUEST_FILE);
        if (type == null) return null;
        Method exists = ReflectionAccess.findMethod(type, "exists", 0);
        if (ReflectionAccess.isStatic(exists)) {
            Object value = ReflectionAccess.invoke(exists, null);
            if (!Boolean.TRUE.equals(value)) return null;
        }
        Method getInstance = ReflectionAccess.findMethod(type, "getInstance", 0);
        if (ReflectionAccess.isStatic(getInstance)) return ReflectionAccess.invoke(getInstance, null);
        return ReflectionAccess.readStaticField(type, "INSTANCE");
    }

    public static Object screenFile(Object screen) {
        return ReflectionAccess.readField(screen, "file");
    }

    public static Object selectedChapter(Object screen) {
        return ReflectionAccess.readField(screen, "selectedChapter");
    }

    public static Object viewedQuest(Object screen) {
        return ReflectionAccess.invokeNoArg(screen, "getViewedQuest");
    }

    public static Object questFileFrom(Object object) {
        Object file = ReflectionAccess.invokeNoArg(object, "getQuestFile");
        return file != null ? file : ReflectionAccess.invokeNoArg(object, "getFile");
    }

    public static Object defaultChapterGroup(Object file) {
        return ReflectionAccess.invokeNoArg(file, "getDefaultChapterGroup");
    }

    public static Object chapterGroups(Object file) {
        return ReflectionAccess.invokeNoArg(file, "getChapterGroups");
    }

    public static Object allChapters(Object file) {
        return ReflectionAccess.invokeNoArg(file, "getAllChapters");
    }

    public static Object visibleChapters(Object file) {
        return ReflectionAccess.invokeNoArg(file, "getVisibleChapters");
    }

    public static Object quests(Object owner) {
        return ReflectionAccess.invokeNoArg(owner, "getQuests");
    }

    public static Object childrenByMethod(Object owner, String methodName) {
        return ReflectionAccess.invokeNoArg(owner, methodName);
    }

    public static Object tasks(Object quest) {
        return ReflectionAccess.invokeNoArg(quest, "getTasks");
    }

    public static Object rewards(Object quest) {
        return ReflectionAccess.invokeNoArg(quest, "getRewards");
    }

    public static Object rawDescription(Object quest) {
        return ReflectionAccess.invokeNoArg(quest, "getRawDescription");
    }

    public static String rawTitle(Object owner) {
        Object value = ReflectionAccess.invokeNoArg(owner, "getRawTitle");
        if (value instanceof String string) return string;
        value = ReflectionAccess.readField(owner, "title");
        return value instanceof String string ? string : null;
    }

    public static String rawSubtitle(Object quest) {
        Object value = ReflectionAccess.invokeNoArg(quest, "getRawSubtitle");
        return value instanceof String string ? string : null;
    }

    public static String stringField(Object target, String name) {
        Object value = ReflectionAccess.readField(target, name);
        return value instanceof String string ? string : null;
    }

    public static Object getTitle(Object object) {
        return ReflectionAccess.invokeNoArg(object, "getTitle");
    }

    public static Object getAltTitle(Object object) {
        return ReflectionAccess.invokeNoArg(object, "getAltTitle");
    }

    public static Object getButtonText(Object object) {
        return ReflectionAccess.invokeNoArg(object, "getButtonText");
    }

    public static Object objectId(Object object) {
        Object id = ReflectionAccess.invokeNoArg(object, "getId");
        return id != null ? id : ReflectionAccess.readField(object, "id");
    }

    public static String codeString(Object object) {
        Object code = ReflectionAccess.invokeNoArg(object, "getCodeString");
        return code instanceof String string && !string.isBlank() ? string : null;
    }

    public static String objectKey(Object object) {
        if (object == null) return "null";
        String code = codeString(object);
        if (code != null) return code;
        Object id = objectId(object);
        if (id != null) return object.getClass().getName() + ':' + id;
        return object.getClass().getName() + '@' + System.identityHashCode(object);
    }

    public static void clearCachedData(Object object) {
        ReflectionAccess.invokeNoArgVoid(object, "clearCachedData");
    }

    public static boolean isClientQuestFile(Object object) {
        return object != null && object.getClass().getName().equals(CLIENT_QUEST_FILE);
    }

    public static boolean isQuestScreen(Object screen) {
        if (screen == null) return false;
        Class<?> type = screen.getClass();
        while (type != null) {
            if (type.getName().startsWith("dev.ftb.mods.ftbquests.client.gui.quests.")) return true;
            type = type.getSuperclass();
        }
        return false;
    }

    public static void refreshQuestScreen(Object screen) {
        if (screen == null) return;
        clearCachedData(screenFile(screen));
        clearCachedData(selectedChapter(screen));
        clearCachedData(viewedQuest(screen));
        ReflectionAccess.invokeNoArgVoid(screen, "refreshChapterPanel");
        ReflectionAccess.invokeNoArgVoid(screen, "refreshQuestPanel");
        ReflectionAccess.invokeNoArgVoid(screen, "refreshViewQuestPanel");
        ReflectionAccess.invokeNoArgVoid(ReflectionAccess.readField(screen, "chapterPanel"), "refreshWidgets");
        ReflectionAccess.invokeNoArgVoid(ReflectionAccess.readField(screen, "questPanel"), "refreshWidgets");
        ReflectionAccess.invokeNoArgVoid(ReflectionAccess.readField(screen, "viewQuestPanel"), "refreshWidgets");
        ReflectionAccess.invokeNoArgVoid(screen, "alignWidgets");
    }

    public static Component parseRawText(String raw, Object registryLookup) {
        Class<?> type = ReflectionAccess.classForName(TEXT_UTILS);
        if (type != null) {
            if (registryLookup != null) {
                Method withLookup = findParseRawTextWithLookup(type);
                Object parsed = ReflectionAccess.invoke(withLookup, null, raw, registryLookup);
                if (parsed instanceof Component component) return component;
            }

            Method simple = ReflectionAccess.findDeclaredMethod(type, "parseRawText", String.class);
            Object parsed = ReflectionAccess.invoke(simple, null, raw);
            if (parsed instanceof Component component) return component;
        }
        return Component.literal(raw == null ? "" : raw);
    }

    private static Method findParseRawTextWithLookup(Class<?> type) {
        for (Method method : type.getDeclaredMethods()) {
            if (!method.getName().equals("parseRawText") || method.getParameterCount() != 2) continue;
            if (method.getParameterTypes()[0] != String.class) continue;
            method.setAccessible(true);
            return method;
        }
        return null;
    }

    public static Object registryLookup() {
        Minecraft mc = Minecraft.getInstance();
        Object level = mc == null ? null : mc.level;
        return ReflectionAccess.invokeNoArg(level, "registryAccess");
    }

    @SuppressWarnings("unchecked")
    public static List<Component> tooltipLines(Object tooltip) {
        Object value = ReflectionAccess.invokeNoArg(tooltip, "getLines");
        return value instanceof List<?> list ? (List<Component>) list : null;
    }
}
