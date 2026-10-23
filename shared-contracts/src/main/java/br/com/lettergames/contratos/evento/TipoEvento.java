package br.com.lettergames.contratos.evento;

/** Fatos que o backlog publica. Eventos (passado), não comandos: ver ADR-002. */
public enum TipoEvento {
    JOGO_ADICIONADO,
    STATUS_ALTERADO,
    JOGO_REMOVIDO
}
