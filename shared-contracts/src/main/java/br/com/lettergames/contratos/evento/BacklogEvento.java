package br.com.lettergames.contratos.evento;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.UUID;

/**
 * Evento publicado em {@link Topicos#BACKLOG_EVENTOS} com chave {@code usuarioId}.
 *
 * <p>{@code eventId} é a chave de idempotência do consumidor (ADR-003).
 * {@code versao} permite evoluir o schema mantendo consumidores antigos.
 *
 * <ul>
 *   <li>{@code JOGO_ADICIONADO}: statusAnterior = null, statusNovo = QUERO_JOGAR (ou o status inicial informado)</li>
 *   <li>{@code STATUS_ALTERADO}: statusAnterior e statusNovo preenchidos</li>
 *   <li>{@code JOGO_REMOVIDO}: statusAnterior = status no momento da remoção, statusNovo = null</li>
 * </ul>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BacklogEvento(
        UUID eventId,
        TipoEvento tipo,
        int versao,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Instant ocorridoEm,
        String usuarioId,
        Long itemId,
        Long jogoId,
        String titulo,
        Integer duracaoMediaHoras,
        StatusBacklog statusAnterior,
        StatusBacklog statusNovo) {

    public static final int VERSAO_ATUAL = 1;

    public static BacklogEvento novo(TipoEvento tipo, String usuarioId, Long itemId, Long jogoId, String titulo,
                                     Integer duracaoMediaHoras, StatusBacklog statusAnterior, StatusBacklog statusNovo) {
        return new BacklogEvento(UUID.randomUUID(), tipo, VERSAO_ATUAL, Instant.now(), usuarioId, itemId, jogoId,
                titulo, duracaoMediaHoras, statusAnterior, statusNovo);
    }
}
