-- Transactional outbox: o evento é gravado na mesma transação do item e publicado depois no Kafka.
CREATE TABLE outbox_eventos (
    event_id       UUID PRIMARY KEY,
    topico         VARCHAR(255)             NOT NULL,
    chave          VARCHAR(100)             NOT NULL,
    tipo           VARCHAR(30)              NOT NULL,
    payload        TEXT                     NOT NULL,
    correlation_id VARCHAR(64),
    criado_em      TIMESTAMP WITH TIME ZONE NOT NULL,
    publicado_em   TIMESTAMP WITH TIME ZONE,
    tentativas     INTEGER                  NOT NULL DEFAULT 0
);

CREATE INDEX ix_outbox_eventos_pendentes ON outbox_eventos (publicado_em, criado_em);
