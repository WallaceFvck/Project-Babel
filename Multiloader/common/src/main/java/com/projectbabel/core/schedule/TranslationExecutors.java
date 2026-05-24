package com.projectbabel.core.schedule;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;

/**
 * Fachada de compatibilidade para o scheduler central.
 *
 * Novos codigos devem depender de TranslationScheduler quando precisarem de
 * prioridade explicita; classes legadas podem continuar usando estes metodos.
 */
public final class TranslationExecutors {

    private TranslationExecutors() {}

    public static ExecutorService triage() {
        return TranslationScheduler.get().triage();
    }

    public static Executor triage(TranslationPriority priority) {
        return TranslationScheduler.get().triage(priority);
    }

    public static ExecutorService io() {
        return TranslationScheduler.get().io();
    }

    public static ExecutorService network() {
        return TranslationScheduler.get().network();
    }

    public static ExecutorService preload() {
        return TranslationScheduler.get().preload();
    }

    public static ExecutorService translationWorkers() {
        return TranslationScheduler.get().translationWorkers();
    }

    public static int workerThreads() {
        return TranslationScheduler.get().workerThreads();
    }

    public static int normalNetworkThreads() {
        return TranslationScheduler.get().normalTranslationLimit();
    }

    public static int turboNetworkThreads() {
        return TranslationScheduler.get().turboTranslationLimit();
    }

    public static int activeTranslationLimit() {
        return TranslationScheduler.get().activeTranslationLimit(PreloadAcceleration.isActive());
    }

    public static void acquireTranslationSlot() throws InterruptedException {
        TranslationScheduler.get().acquireTranslationSlot(PreloadAcceleration.isActive());
    }

    public static void releaseTranslationSlot() {
        TranslationScheduler.get().releaseTranslationSlot();
    }

    public static int activeTranslationCount() {
        return TranslationScheduler.get().activeTranslationCount();
    }

    public static String modeSummary() {
        return TranslationScheduler.get().modeSummary();
    }

    public static void shutdown() {
        TranslationScheduler.get().shutdownNow();
    }
}
