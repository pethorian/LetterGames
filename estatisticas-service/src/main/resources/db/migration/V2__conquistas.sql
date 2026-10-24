-- Projeção própria do consumer group "conquistas": só cresce (marcos atingidos não são desfeitos).
CREATE TABLE progresso_conquistas (
    usuario_id    VARCHAR(100) NOT NULL PRIMARY KEY,
    zeramentos    INTEGER      NOT NULL DEFAULT 0,
    horas_zeradas INTEGER      NOT NULL DEFAULT 0,
    abandonos     INTEGER      NOT NULL DEFAULT 0,
    versao        BIGINT       NOT NULL
);

CREATE TABLE conquistas (
    usuario_id      VARCHAR(100)             NOT NULL,
    codigo          VARCHAR(40)              NOT NULL,
    desbloqueada_em TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (usuario_id, codigo)
);
