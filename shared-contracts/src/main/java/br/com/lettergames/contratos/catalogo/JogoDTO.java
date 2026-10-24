package br.com.lettergames.contratos.catalogo;

/**
 * Representação pública de um jogo do catálogo: resposta de {@code GET /jogos/{id}}.
 * Este é o formato coberto pelo pact backlog-service → catalogo-service (ADR-006).
 */
public record JogoDTO(
        Long id,
        String titulo,
        String plataforma,
        String genero,
        Integer duracaoMediaHoras) {
}
