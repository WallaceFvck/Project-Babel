package com.projectbabel.integrations.registry;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shared reflection helper with small caches for optional-mod integrations.
 */
public final class ReflectionAccess {

    private static final ConcurrentHashMap<MemberKey, Method> METHODS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<MemberKey, Field> FIELDS = new ConcurrentHashMap<>();

    private ReflectionAccess() {}

    public static Object readField(Object target, String name) {
        if (target == null || name == null || name.isBlank()) return null;
        try {
            Field field = findField(target.getClass(), name);
            if (field == null) return null;
            return field.get(target);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static boolean writeField(Object target, String name, Object value) {
        if (target == null || name == null || name.isBlank()) return false;
        try {
            Field field = findField(target.getClass(), name);
            if (field == null) return false;
            field.set(target, value);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static Object invokeNoArg(Object target, String methodName) {
        if (target == null || methodName == null || methodName.isBlank()) return null;
        try {
            Method method = findNoArgMethod(target.getClass(), methodName);
            if (method == null) return null;
            return method.invoke(target);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static boolean tryInvokeNoArg(Object target, String methodName) {
        if (target == null || methodName == null || methodName.isBlank()) return false;
        try {
            Method method = findNoArgMethod(target.getClass(), methodName);
            if (method == null) return false;
            method.invoke(target);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static Method findNoArgMethod(Class<?> type, String methodName) {
        if (type == null || methodName == null || methodName.isBlank()) return null;
        MemberKey key = new MemberKey(type, methodName);
        Method cached = METHODS.get(key);
        if (cached != null) return cached;

        Class<?> current = type;
        while (current != null) {
            try {
                Method method = current.getDeclaredMethod(methodName);
                if (method.getParameterCount() == 0) {
                    method.setAccessible(true);
                    METHODS.putIfAbsent(key, method);
                    return method;
                }
            } catch (NoSuchMethodException ignored) {
                current = current.getSuperclass();
            } catch (Throwable ignored) {
                return null;
            }
        }
        return null;
    }

    public static Field findField(Class<?> type, String name) {
        if (type == null || name == null || name.isBlank()) return null;
        MemberKey key = new MemberKey(type, name);
        Field cached = FIELDS.get(key);
        if (cached != null) return cached;

        Class<?> current = type;
        while (current != null) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                FIELDS.putIfAbsent(key, field);
                return field;
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            } catch (Throwable ignored) {
                return null;
            }
        }
        return null;
    }

    private record MemberKey(Class<?> type, String name) {
        private MemberKey {
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(name, "name");
        }
    }
}
