package com.projectbabel.core.schedule;

/**
 * Prioridade logica das tarefas de traducao.
 * Menor peso = executa antes quando compartilha o mesmo executor.
 */
public enum TranslationPriority {
    HIGH(0),
    NORMAL(10),
    LOW(20);

    private final int weight;

    TranslationPriority(int weight) {
        this.weight = weight;
    }

    public int weight() {
        return weight;
    }
}
