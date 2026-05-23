package com.projectbabel.translation;

/**
 * Guard thread-local para evitar que o mod tente traduzir seus próprios textos.
 *
 * Usa contador em vez de booleano para suportar chamadas aninhadas:
 * se o overlay chama drawString que chama outro drawString internamente,
 * o guard permanece ativo até que todos os níveis saiam.
 *
 * ThreadLocal: cada thread tem seu próprio estado independente.
 */
public final class RenderingGuard {

    // Contador de profundidade — ativo quando > 0
    private static final ThreadLocal<Integer> DEPTH = ThreadLocal.withInitial(() -> 0);

    private RenderingGuard() {}

    /** Ativa o guard (incrementa profundidade). */
    public static void enter() {
        DEPTH.set(DEPTH.get() + 1);
    }

    /** Desativa um nível do guard. Sempre chamar em finally. */
    public static void exit() {
        int d = DEPTH.get() - 1;
        DEPTH.set(Math.max(0, d)); // nunca vai abaixo de 0
    }

    /** True se estamos dentro de qualquer nível de renderização interna. */
    public static boolean isActive() {
        return DEPTH.get() > 0;
    }

    /**
     * Executa um bloco protegido — garante exit() mesmo em exceção.
     */
    public static void run(Runnable block) {
        enter();
        try {
            block.run();
        } finally {
            exit();
        }
    }
}
