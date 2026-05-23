package com.projectbabel.translation;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Marca escopos de preload para liberar o limite mais agressivo de traducao
 * apenas enquanto dados grandes estao sendo aquecidos antes da interface abrir.
 */
public final class PreloadAcceleration {

    private static final AtomicInteger DEPTH = new AtomicInteger(0);

    private PreloadAcceleration() {}

    public static boolean isActive() {
        return DEPTH.get() > 0;
    }

    public static void run(Runnable runnable) {
        DEPTH.incrementAndGet();
        try {
            runnable.run();
        } finally {
            exit();
        }
    }

    public static <T> T supply(Supplier<T> supplier) {
        DEPTH.incrementAndGet();
        try {
            return supplier.get();
        } finally {
            exit();
        }
    }

    private static void exit() {
        DEPTH.updateAndGet(value -> Math.max(0, value - 1));
    }
}
