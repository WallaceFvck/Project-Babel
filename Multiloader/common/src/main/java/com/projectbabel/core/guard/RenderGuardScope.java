package com.projectbabel.core.guard;

/** AutoCloseable scope for render guards. Prefer try-with-resources in new code. */
public final class RenderGuardScope implements AutoCloseable {
    private boolean closed;

    RenderGuardScope() {}

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        RenderGuardState.exit();
    }
}
