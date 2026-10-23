-- Evento que falhou max-tentativas vezes fica estacionado: sai da busca e bloqueia os seguintes da mesma chave.
ALTER TABLE outbox_eventos ADD COLUMN falhou_em TIMESTAMP WITH TIME ZONE;

-- A busca de pendentes procura a cabeça de cada chave (o mais antigo não publicado daquele usuário).
CREATE INDEX ix_outbox_eventos_chave_pendentes ON outbox_eventos (chave, publicado_em, criado_em);
