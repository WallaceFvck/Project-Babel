package com.projectbabel.integrations.create;

import com.projectbabel.integrations.access.ReflectionAccess;
import net.minecraft.resources.ResourceLocation;

import java.lang.reflect.Method;
import java.util.Map;

/** Semantic reflection boundary for Create Ponder localization internals. */
public final class CreatePonderAccess {
    private static final String[] LOCALIZATION_CLASSES = {
        "com.simibubi.create.foundation.ponder.PonderLocalization",
        "net.createmod.ponder.foundation.PonderLocalization"
    };

    private CreatePonderAccess() {}

    public static String shared(ResourceLocation id) {
        return mapString("SHARED", id);
    }

    public static String tag(ResourceLocation id) {
        return coupleString("TAG", id, true);
    }

    public static String tagDescription(ResourceLocation id) {
        return coupleString("TAG", id, false);
    }

    public static String chapter(ResourceLocation id) {
        return mapString("CHAPTER", id);
    }

    public static String specific(ResourceLocation id, String key) {
        Object map = readStaticField("SPECIFIC");
        if (!(map instanceof Map<?, ?> values)) return null;
        Object nested = values.get(id);
        if (!(nested instanceof Map<?, ?> specific)) return null;
        Object value = specific.get(key);
        return value instanceof String text ? text : null;
    }

    private static String mapString(String fieldName, ResourceLocation id) {
        Object map = readStaticField(fieldName);
        if (!(map instanceof Map<?, ?> values)) return null;
        Object value = values.get(id);
        return value instanceof String text ? text : null;
    }

    private static String coupleString(String fieldName, ResourceLocation id, boolean first) {
        Object map = readStaticField(fieldName);
        if (!(map instanceof Map<?, ?> values)) return null;
        Object couple = values.get(id);
        if (couple == null) return null;
        Method method = ReflectionAccess.findMethod(couple.getClass(), first ? "getFirst" : "getSecond", 0);
        Object value = ReflectionAccess.invoke(method, couple);
        return value instanceof String text ? text : null;
    }

    private static Object readStaticField(String fieldName) {
        for (String className : LOCALIZATION_CLASSES) {
            Object value = ReflectionAccess.readStaticField(ReflectionAccess.classForName(className), fieldName);
            if (value != null) return value;
        }
        return null;
    }
}
