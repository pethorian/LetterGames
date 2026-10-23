-- Projeção de estatísticas por usuário (derivada dos eventos do backlog).
CREATE TABLE estatisticas_usuario (
    usuario_id      VARCHAR(100)             NOT NULL PRIMARY KEY,
    quero_jogar     INTEGER                  NOT NULL DEFAULT 0,
    jogando         INTEGER                  NOT NULL DEFAULT 0,
    zerados         INTEGER                  NOT NULL DEFAULT 0,
    abandonados     INTEGER                  NOT NULL DEFAULT 0,
    horas_pendentes INTEGER                  NOT NULL DEFAULT 0,
    horas_zeradas   INTEGER                  NOT NULL DEFAULT 0,
    atualizado_em   TIMESTAMP WITH TIME ZONE NOT NULL,
    versao          BIGINT                   NOT NULL
);

-- Idempotência (ADR-003): uma linha por (evento, consumer group), gravada na mesma transação do efeito.
CREATE TABLE eventos_processados (
    event_id      UUID                     NOT NULL,
    consumidor    VARCHAR(40)              NOT NULL,
    processado_em TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (event_id, consumidor)
);
