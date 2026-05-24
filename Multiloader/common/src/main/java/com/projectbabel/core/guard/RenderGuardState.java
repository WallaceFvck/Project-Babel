package com.projectbabel.core.guard;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumSet;
import java.util.Set;

/**
 * Thread-local render guard state.
 *
 * The old RenderingGuard API exposed a single depth counter. This class keeps
 * the same nested behavior but stores the reason for each entry, making it
 * easier to separate Project Babel UI guards from mod-specific layout guards.
 */
public final class RenderGuardState {

    private static final ThreadLocal<Deque<RenderGuardReason>> STACK =
        ThreadLocal.withInitial(ArrayDeque::new);

    private RenderGuardState() {}

    public static RenderGuardScope enter(RenderGuardReason reason) {
        STACK.get().push(reason == null ? RenderGuardReason.LEGACY : reason);
        return new RenderGuardScope();
    }

    public static void exit() {
        Deque<RenderGuardReason> stack = STACK.get();
        if (!stack.isEmpty()) {
            stack.pop();
        }
        if (stack.isEmpty()) {
            STACK.remove();
        }
    }

    public static boolean isActive() {
        return !STACK.get().isEmpty();
    }

    public static boolean isActive(RenderGuardReason reason) {
        return reason != null && STACK.get().contains(reason);
    }

    public static int depth() {
        return STACK.get().size();
    }

    public static Set<RenderGuardReason> activeReasons() {
        Deque<RenderGuardReason> stack = STACK.get();
        if (stack.isEmpty()) return EnumSet.noneOf(RenderGuardReason.class);
        EnumSet<RenderGuardReason> reasons = EnumSet.noneOf(RenderGuardReason.class);
        reasons.addAll(stack);
        return reasons;
    }

    public static void run(RenderGuardReason reason, Runnable block) {
        try (RenderGuardScope ignored = enter(reason)) {
            block.run();
        }
    }
}
