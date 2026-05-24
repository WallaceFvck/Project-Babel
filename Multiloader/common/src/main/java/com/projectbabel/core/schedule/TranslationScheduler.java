package com.projectbabel.core.schedule;

import com.projectbabel.ProjectBabelCommon;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Scheduler central de concorrencia do Project Babel.
 *
 * A Fase 6 troca o modelo antigo de muitos pools independentes por um ponto
 * unico para triage, IO, rede, preload e workers de traducao. O limite real
 * de chamadas simultaneas e dinamico: modo normal, modo turbo e preload usam
 * perfis diferentes sem exigir milhares de threads fixas.
 */
public final class TranslationScheduler {

    private static final int CPU_COUNT = Math.max(1, Runtime.getRuntime().availableProcessors());

    private static final int TRIAGE_THREADS = clamp(CPU_COUNT, 2, 8);
    private static final int IO_THREADS = clamp(Math.max(2, CPU_COUNT / 2), 2, 8);
    private static final int NETWORK_THREADS = clamp(CPU_COUNT * 8, 16, 128);
    private static final int PRELOAD_THREADS = clamp(CPU_COUNT * 8, 16, 128);
    private static final int WORKER_THREADS = 256;

    private static final int NORMAL_LIMIT_CAP = 32;
    private static final int TURBO_LIMIT_CAP = 128;
    private static final int PRELOAD_LIMIT_CAP = 256;

    private static final TranslationScheduler INSTANCE = new TranslationScheduler();

    private final AtomicInteger threadCounter = new AtomicInteger();
    private final AtomicInteger activeTranslations = new AtomicInteger(0);
    private final AtomicBoolean shutdown = new AtomicBoolean(false);

    private final PrioritizedExecutorService triage;
    private final ExecutorService io;
    private final ExecutorService network;
    private final ExecutorService preload;
    private final ExecutorService translationWorkers;

    private TranslationScheduler() {
        this.triage = new PrioritizedExecutorService(
            TRIAGE_THREADS,
            namedFactory("projectbabel-Triage", Thread.NORM_PRIORITY - 1)
        );
        this.io = new ThreadPoolExecutor(
            IO_THREADS,
            IO_THREADS,
            0L,
            TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>(16384),
            namedFactory("projectbabel-IO", Thread.NORM_PRIORITY - 1),
            new ThreadPoolExecutor.CallerRunsPolicy()
        );
        this.network = new ThreadPoolExecutor(
            NETWORK_THREADS,
            NETWORK_THREADS,
            0L,
            TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>(65536),
            namedFactory("projectbabel-Net", Thread.NORM_PRIORITY),
            new ThreadPoolExecutor.CallerRunsPolicy()
        );
        this.preload = new ThreadPoolExecutor(
            PRELOAD_THREADS,
            PRELOAD_THREADS,
            0L,
            TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>(262144),
            namedFactory("projectbabel-Preload", Thread.NORM_PRIORITY - 1),
            new ThreadPoolExecutor.CallerRunsPolicy()
        );
        this.translationWorkers = Executors.newFixedThreadPool(
            WORKER_THREADS,
            namedFactory("projectbabel-Translator", Thread.NORM_PRIORITY)
        );
    }

    public static TranslationScheduler get() {
        return INSTANCE;
    }

    public ExecutorService triage() {
        return triage;
    }

    public ExecutorService io() {
        return io;
    }

    public ExecutorService network() {
        return network;
    }

    public ExecutorService preload() {
        return preload;
    }

    public ExecutorService translationWorkers() {
        return translationWorkers;
    }

    public Executor triage(TranslationPriority priority) {
        return command -> triage.execute(command, priority);
    }

    public int workerThreads() {
        return WORKER_THREADS;
    }

    public int activeTranslationLimit(boolean preloadActive) {
        int configured = Math.max(1, ProjectBabelCommon.config().getMaxConcurrentRequests());
        if (preloadActive) return Math.max(1, Math.min(configured, PRELOAD_LIMIT_CAP));
        return ProjectBabelCommon.config().isTurboMode()
            ? Math.max(1, Math.min(configured, TURBO_LIMIT_CAP))
            : Math.max(1, Math.min(configured, NORMAL_LIMIT_CAP));
    }

    public void acquireTranslationSlot(boolean preloadActive) throws InterruptedException {
        while (!shutdown.get() && !Thread.currentThread().isInterrupted()) {
            int active = activeTranslations.get();
            int limit = activeTranslationLimit(preloadActive);
            if (active < limit && activeTranslations.compareAndSet(active, active + 1)) {
                return;
            }
            Thread.sleep(10L);
        }
        throw new InterruptedException("Project Babel scheduler is shutting down");
    }

    public void releaseTranslationSlot() {
        activeTranslations.updateAndGet(value -> Math.max(0, value - 1));
    }

    public int activeTranslationCount() {
        return activeTranslations.get();
    }

    public int normalTranslationLimit() {
        return Math.max(1, Math.min(ProjectBabelCommon.config().getMaxConcurrentRequests(), NORMAL_LIMIT_CAP));
    }

    public int turboTranslationLimit() {
        return Math.max(1, Math.min(ProjectBabelCommon.config().getMaxConcurrentRequests(), TURBO_LIMIT_CAP));
    }

    public int preloadTranslationLimit() {
        return Math.max(1, Math.min(ProjectBabelCommon.config().getMaxConcurrentRequests(), PRELOAD_LIMIT_CAP));
    }

    public String modeSummary() {
        return "threads{triage=" + TRIAGE_THREADS
            + ", io=" + IO_THREADS
            + ", network=" + NETWORK_THREADS
            + ", preload=" + PRELOAD_THREADS
            + ", workers=" + WORKER_THREADS
            + "}, limits{normal=" + normalTranslationLimit()
            + ", turbo=" + turboTranslationLimit()
            + ", preload=" + preloadTranslationLimit()
            + "}";
    }

    public void shutdownNow() {
        if (!shutdown.compareAndSet(false, true)) return;
        translationWorkers.shutdownNow();
        preload.shutdownNow();
        network.shutdownNow();
        io.shutdownNow();
        triage.shutdownNow();
    }

    private ThreadFactory namedFactory(String prefix, int priority) {
        return runnable -> {
            Thread thread = new Thread(runnable, prefix + '-' + threadCounter.incrementAndGet());
            thread.setDaemon(true);
            thread.setPriority(priority);
            return thread;
        };
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * Executor pequeno com fila priorizada. Chamadas sem prioridade explicita
     * entram como NORMAL, preservando compatibilidade com CompletableFuture.
     */
    private static final class PrioritizedExecutorService extends ThreadPoolExecutor {
        private final AtomicLong sequence = new AtomicLong();

        private PrioritizedExecutorService(int threads, ThreadFactory factory) {
            super(
                threads,
                threads,
                0L,
                TimeUnit.MILLISECONDS,
                new PriorityBlockingQueue<>(),
                factory,
                new ThreadPoolExecutor.CallerRunsPolicy()
            );
        }

        @Override
        public void execute(Runnable command) {
            execute(command, TranslationPriority.NORMAL);
        }

        public void execute(Runnable command, TranslationPriority priority) {
            if (command == null) throw new NullPointerException("command");
            if (command instanceof PrioritizedRunnable) {
                super.execute(command);
                return;
            }
            super.execute(new PrioritizedRunnable(command, priority, sequence.incrementAndGet()));
        }
    }

    private static final class PrioritizedRunnable implements Runnable, Comparable<PrioritizedRunnable> {
        private final Runnable delegate;
        private final TranslationPriority priority;
        private final long sequence;

        private PrioritizedRunnable(Runnable delegate, TranslationPriority priority, long sequence) {
            this.delegate = delegate;
            this.priority = priority == null ? TranslationPriority.NORMAL : priority;
            this.sequence = sequence;
        }

        @Override
        public void run() {
            delegate.run();
        }

        @Override
        public int compareTo(PrioritizedRunnable other) {
            int byPriority = Integer.compare(priority.weight(), other.priority.weight());
            return byPriority != 0 ? byPriority : Long.compare(sequence, other.sequence);
        }
    }
}
