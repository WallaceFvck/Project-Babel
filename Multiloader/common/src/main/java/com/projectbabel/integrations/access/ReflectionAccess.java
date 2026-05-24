package com.projectbabel.integrations.access;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shared low-level reflection helper used only by integration accessors.
 *
 * Runtime mod APIs are intentionally hidden behind semantic accessors so the
 * rest of Project Babel does not depend on field names, method names or
 * fallback class names from third-party mods.
 */
public final class ReflectionAccess {

    private static final Map<String, Class<?>> CLASS_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Method> METHOD_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Field> FIELD_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> MISSING = new ConcurrentHashMap<>();

    private ReflectionAccess() {}

    public static Class<?> findClass(String... classNames) {
        if (classNames == null) return null;
        for (String className : classNames) {
            Class<?> type = classForName(className);
            if (type != null) return type;
        }
        return null;
    }

    public static Class<?> classForName(String className) {
        if (className == null || className.isBlank()) return null;
        String key = "class:" + className;
        if (MISSING.containsKey(key)) return null;
        Class<?> cached = CLASS_CACHE.get(className);
        if (cached != null) return cached;
        try {
            Class<?> type = Class.forName(className, false, Thread.currentThread().getContextClassLoader());
            CLASS_CACHE.put(className, type);
            return type;
        } catch (Throwable ignored) {
            try {
                Class<?> type = Class.forName(className);
                CLASS_CACHE.put(className, type);
                return type;
            } catch (Throwable ignoredAgain) {
                MISSING.put(key, Boolean.TRUE);
                return null;
            }
        }
    }

    public static boolean classExists(String className) {
        return classForName(className) != null;
    }

    public static Object invokeNoArg(Object target, String methodName) {
        if (target == null) return null;
        try {
            Method method = findMethod(target.getClass(), methodName, 0);
            return method == null ? null : method.invoke(target);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static boolean invokeNoArgBoolean(Object target, String methodName) {
        Object value = invokeNoArg(target, methodName);
        return Boolean.TRUE.equals(value);
    }

    public static boolean invokeAnyNoArg(Object target, String... methodNames) {
        if (target == null || methodNames == null) return false;
        for (String methodName : methodNames) {
            if (invokeNoArgVoid(target, methodName)) return true;
        }
        return false;
    }

    public static boolean invokeNoArgVoid(Object target, String methodName) {
        if (target == null) return false;
        try {
            Method method = findMethod(target.getClass(), methodName, 0);
            if (method == null) return false;
            method.invoke(target);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static Object invoke(Method method, Object target, Object... args) {
        if (method == null) return null;
        try {
            return method.invoke(target, args);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static Object invokeStaticNoArg(Class<?> type, String methodName) {
        if (type == null) return null;
        try {
            Method method = findMethod(type, methodName, 0);
            if (method == null || !Modifier.isStatic(method.getModifiers())) return null;
            return method.invoke(null);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static Object readField(Object target, String fieldName) {
        if (target == null) return null;
        try {
            Field field = findField(target.getClass(), fieldName);
            return field == null ? null : field.get(target);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static Object readStaticField(Class<?> type, String fieldName) {
        if (type == null) return null;
        try {
            Field field = findField(type, fieldName);
            if (field == null || !Modifier.isStatic(field.getModifiers())) return null;
            return field.get(null);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static Method findDeclaredMethod(Class<?> type, String methodName, Class<?>... parameterTypes) {
        if (type == null || methodName == null) return null;
        String key = methodKey(type, methodName, parameterTypes);
        if (MISSING.containsKey(key)) return null;
        Method cached = METHOD_CACHE.get(key);
        if (cached != null) return cached;

        Class<?> current = type;
        while (current != null) {
            try {
                Method method = current.getDeclaredMethod(methodName, parameterTypes);
                method.setAccessible(true);
                METHOD_CACHE.put(key, method);
                return method;
            } catch (NoSuchMethodException ignored) {
                current = current.getSuperclass();
            } catch (Throwable ignored) {
                MISSING.put(key, Boolean.TRUE);
                return null;
            }
        }

        try {
            Method method = type.getMethod(methodName, parameterTypes);
            method.setAccessible(true);
            METHOD_CACHE.put(key, method);
            return method;
        } catch (Throwable ignored) {
            MISSING.put(key, Boolean.TRUE);
            return null;
        }
    }

    public static Method findMethod(Class<?> type, String methodName, int parameterCount) {
        if (type == null || methodName == null) return null;
        String key = type.getName() + '#' + methodName + '/' + parameterCount;
        if (MISSING.containsKey(key)) return null;
        Method cached = METHOD_CACHE.get(key);
        if (cached != null) return cached;

        Class<?> current = type;
        while (current != null) {
            for (Method method : current.getDeclaredMethods()) {
                if (method.getName().equals(methodName) && method.getParameterCount() == parameterCount) {
                    method.setAccessible(true);
                    METHOD_CACHE.put(key, method);
                    return method;
                }
            }
            current = current.getSuperclass();
        }

        for (Method method : type.getMethods()) {
            if (method.getName().equals(methodName) && method.getParameterCount() == parameterCount) {
                method.setAccessible(true);
                METHOD_CACHE.put(key, method);
                return method;
            }
        }

        MISSING.put(key, Boolean.TRUE);
        return null;
    }

    public static Field findField(Class<?> type, String fieldName) {
        if (type == null || fieldName == null) return null;
        String key = type.getName() + '#' + fieldName;
        if (MISSING.containsKey(key)) return null;
        Field cached = FIELD_CACHE.get(key);
        if (cached != null) return cached;

        Class<?> current = type;
        while (current != null) {
            try {
                Field field = current.getDeclaredField(fieldName);
                field.setAccessible(true);
                FIELD_CACHE.put(key, field);
                return field;
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            } catch (Throwable ignored) {
                MISSING.put(key, Boolean.TRUE);
                return null;
            }
        }

        MISSING.put(key, Boolean.TRUE);
        return null;
    }

    public static boolean invokeScreenInit(Screen screen, Minecraft mc) {
        if (screen == null || mc == null) return false;
        try {
            Method method = findDeclaredMethod(Screen.class, "init", Minecraft.class, int.class, int.class);
            if (method == null) return false;
            method.invoke(screen, mc, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static Object newInstanceMatchingSingleArg(Class<?> type, Object argument) {
        if (type == null || argument == null) return null;
        for (Constructor<?> constructor : type.getDeclaredConstructors()) {
            Class<?>[] parameters = constructor.getParameterTypes();
            if (parameters.length != 1 || !parameters[0].isAssignableFrom(argument.getClass())) continue;
            try {
                constructor.setAccessible(true);
                return constructor.newInstance(argument);
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    public static boolean isStatic(Method method) {
        return method != null && Modifier.isStatic(method.getModifiers());
    }

    private static String methodKey(Class<?> type, String methodName, Class<?>... parameterTypes) {
        StringBuilder builder = new StringBuilder(type.getName()).append('#').append(methodName).append('(');
        if (parameterTypes != null) {
            for (Class<?> parameterType : parameterTypes) {
                builder.append(parameterType == null ? "null" : parameterType.getName()).append(',');
            }
        }
        return builder.append(')').toString();
    }
}
