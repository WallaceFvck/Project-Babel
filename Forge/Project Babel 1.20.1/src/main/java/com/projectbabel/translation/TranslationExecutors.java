package com.projectbabel.translation;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Centraliza os executores do mod para evitar uso acidental do commonPool e
 * manter concorrencia previsivel durante preload, chat sincrono e triagem.
 */
public final class TranslationExecutors {

    private static final int CPU_COUNT = Math.max(1, Runtime.getRuntime().availableProcessors());
    private static final int NORMAL_TRIAGE_THREADS = Math.max(4, Math.min(16, CPU_COUNT));
    private static final int NORMAL_IO_THREADS = Math.max(4, Math.min(32, CPU_COUNT * 2));
    private static final int NORMAL_NETWORK_THREADS = Math.max(4, Math.min(32, CPU_COUNT * 2));
    private static final int TURBO_TRIAGE_THREADS = Math.max(32, Math.min(256, CPU_COUNT * 8));
    private static final int TURBO_IO_THREADS = Math.max(64, Math.min(512, CPU_COUNT * 16));
    private static final int TURBO_NETWORK_THREADS = Math.max(128, Math.min(1024, CPU_COUNT * 32));
    private static final int PRELOAD_THREADS = Math.max(128, Math.min(1024, CPU_COUNT * 32));
    private static final AtomicInteger THREAD_COUNTER = new AtomicInteger(0);

    private static final ExecutorService TRIAGE = Executors.newFixedThreadPool(
        TURBO_TRIAGE_THREADS,
        namedFactory("projectbabel-Triage", Thread.NORM_PRIORITY - 1)
    );

    private static final ExecutorService IO = Executors.newFixedThreadPool(
        TURBO_IO_THREADS,
        namedFactory("projectbabel-IO", Thread.NORM_PRIORITY)
    );

    private static final ExecutorService NETWORK = Executors.newFixedThreadPool(
        TURBO_NETWORK_THREADS,
        namedFactory("projectbabel-Net", Thread.NORM_PRIORITY)
    );

    private static final ExecutorService PRELOAD = Executors.newFixedThreadPool(
        PRELOAD_THREADS,
        namedFactory("projectbabel-Preload", Math.min(Thread.MAX_PRIORITY, Thread.NORM_PRIORITY + 1))
    );

    private TranslationExecutors() {}

    public static ExecutorService triage() {
        return TRIAGE;
    }

    public static ExecutorService io() {
        return IO;
    }

    public static ExecutorService network() {
        return NETWORK;
    }

    public static ExecutorService preload() {
        return PRELOAD;
    }

    public static int normalNetworkThreads() {
        return NORMAL_NETWORK_THREADS;
    }

    public static int turboNetworkThreads() {
        return TURBO_NETWORK_THREADS;
    }

    public static String modeSummary() {
        return "normal=" + NORMAL_NETWORK_THREADS + ", turbo=" + TURBO_NETWORK_THREADS + ", preload=" + PRELOAD_THREADS;
    }

    public static void shutdown() {
        TRIAGE.shutdownNow();
        IO.shutdownNow();
        NETWORK.shutdownNow();
        PRELOAD.shutdownNow();
    }

    private static ThreadFactory namedFactory(String prefix, int priority) {
        return runnable -> {
            Thread thread = new Thread(runnable, prefix + '-' + THREAD_COUNTER.incrementAndGet());
            thread.setDaemon(true);
            thread.setPriority(priority);
            return thread;
        };
    }
}
