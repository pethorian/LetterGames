package br.com.lettergames.estatisticas.conquista;

import java.util.function.Predicate;

/** Catálogo de conquistas e o critério de cada uma sobre o progresso acumulado. */
public enum TipoConquista {

    PRIMEIRO_ZERADO("Primeiro zerado!", p -> p.getZeramentos() >= 1),
    CINCO_ZERADOS("Cinco jogos zerados", p -> p.getZeramentos() >= 5),
    MARATONISTA("Maratonista: 100h zeradas", p -> p.getHorasZeradas() >= 100),
    DESAPEGADO("Desapegado: abandonou um jogo sem culpa", p -> p.getAbandonos() >= 1);

    private final String descricao;
    private final Predicate<ProgressoConquistas> criterio;

    TipoConquista(String descricao, Predicate<ProgressoConquistas> criterio) {
        this.descricao = descricao;
        this.criterio = criterio;
    }

    public String descricao() {
        return descricao;
    }

    public boolean atingida(ProgressoConquistas progresso) {
        return criterio.test(progresso);
    }
}
