-- Lock otimista (@Version) como rede de segurança do lock pessimista em PATCH/DELETE.
ALTER TABLE itens_backlog ADD COLUMN versao BIGINT NOT NULL DEFAULT 0;
