package com.projectbabel.debug;

import com.projectbabel.ProjectBabelCommon;

import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class ProjectBabelDebug {

    public static final String TOOLTIP = "tooltip";
    public static final String QUESTS = "quests";
    public static final String BOOKS = "books";
    public static final String PONDER = "ponder";

    private static final int MAX_LOGS_PER_SCOPE = 500;
    private static final ConcurrentHashMap<String, AtomicInteger> COUNTS = new ConcurrentHashMap<>();

    private ProjectBabelDebug() {}

    public static boolean enabled(String scope) {
        return ProjectBabelCommon.config().isDebugScope(scope);
    }

    public static void resetSessionCounters() {
        COUNTS.clear();
    }

    public static void info(String scope, String message, Object... args) {
        if (!enabled(scope)) return;
        if (!allow(scope)) return;
        ProjectBabelCommon.LOGGER.info("[projectbabel][debug:{}] " + message, prependScope(scope, args));
    }

    public static void warn(String scope, String message, Object... args) {
        if (!enabled(scope)) return;
        if (!allow(scope)) return;
        ProjectBabelCommon.LOGGER.warn("[projectbabel][debug:{}] " + message, prependScope(scope, args));
    }

    private static boolean allow(String scope) {
        String key = normalize(scope);
        AtomicInteger count = COUNTS.computeIfAbsent(key, ignored -> new AtomicInteger());
        int value = count.incrementAndGet();
        if (value == MAX_LOGS_PER_SCOPE + 1) {
            ProjectBabelCommon.LOGGER.warn(
                "[projectbabel][debug:{}] limite de {} logs atingido nesta sessao.",
                key,
                MAX_LOGS_PER_SCOPE
            );
        }
        return value <= MAX_LOGS_PER_SCOPE;
    }

    private static String normalize(String scope) {
        if (scope == null || scope.isBlank()) return "unknown";
        return scope.trim().toLowerCase(Locale.ROOT);
    }

    private static Object[] prependScope(String scope, Object[] args) {
        Object[] result = new Object[(args == null ? 0 : args.length) + 1];
        result[0] = normalize(scope);
        if (args != null && args.length > 0) {
            System.arraycopy(args, 0, result, 1, args.length);
        }
        return result;
    }
}
