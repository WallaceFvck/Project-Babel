package com.projectbabel.core.guard;


import java.util.Set;

/**
 * Compatibility facade for render-translation guards.
 *
 * New code should prefer scope(RenderGuardReason) or run(RenderGuardReason,...)
 * so the reason for bypassing generic Font/GuiGraphics hooks remains explicit.
 */
public final class RenderingGuard {

    private RenderingGuard() {}

    /** Legacy entry point: protects Project Babel/internal rendering. */
    public static void enter() {
        RenderGuardState.enter(RenderGuardReason.INTERNAL_UI);
    }

    /** Reason-aware entry point for mod-specific render guards. */
    public static void enter(RenderGuardReason reason) {
        RenderGuardState.enter(reason);
    }

    /** Prefer try-with-resources in new code. */
    public static RenderGuardScope scope(RenderGuardReason reason) {
        return RenderGuardState.enter(reason);
    }

    /** Desativa um nível do guard. Sempre chamar em finally. */
    public static void exit() {
        RenderGuardState.exit();
    }

    /** True se estamos dentro de qualquer nível de renderização protegida. */
    public static boolean isActive() {
        return RenderGuardState.isActive();
    }

    public static boolean isActive(RenderGuardReason reason) {
        return RenderGuardState.isActive(reason);
    }

    public static int depth() {
        return RenderGuardState.depth();
    }

    public static Set<RenderGuardReason> activeReasons() {
        return RenderGuardState.activeReasons();
    }

    /** Executa um bloco protegido — garante exit() mesmo em exceção. */
    public static void run(Runnable block) {
        run(RenderGuardReason.INTERNAL_UI, block);
    }

    public static void run(RenderGuardReason reason, Runnable block) {
        RenderGuardState.run(reason, block);
    }
}
