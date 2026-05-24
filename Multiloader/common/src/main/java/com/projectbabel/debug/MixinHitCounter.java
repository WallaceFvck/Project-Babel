package com.projectbabel.debug;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Platform-neutral counter used by common vanilla mixins to report that a render/text hook fired.
 * Platform health checks drain this value from their own tick/event systems.
 */
public final class MixinHitCounter {
    private static final AtomicLong HITS = new AtomicLong(0);

    private MixinHitCounter() {}

    public static void hit() {
        HITS.incrementAndGet();
    }

    public static long drain() {
        return HITS.getAndSet(0);
    }
}
