package br.com.lettergames.estatisticas.consumo;

import br.com.lettergames.contratos.evento.BacklogEvento;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/**
 * Converte o value (String JSON) em {@link BacklogEvento} e valida o mínimo para aplicar o efeito.
 * A desserialização fica aqui, e não no deserializer do Kafka, para que o value original siga intacto para a DLT.
 */
@Component
public class LeitorEventos {

    private final ObjectMapper objectMapper;

    public LeitorEventos(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public BacklogEvento ler(String json) {
        if (json == null || json.isBlank()) {
            throw new EventoInvalidoException("value vazio");
        }
        BacklogEvento evento;
        try {
            evento = objectMapper.readValue(json, BacklogEvento.class);
        } catch (JsonProcessingException e) {
            throw new EventoInvalidoException("JSON inválido: " + e.getOriginalMessage(), e);
        }
        validar(evento);
        return evento;
    }

    private static void validar(BacklogEvento evento) {
        if (evento == null) {
            throw new EventoInvalidoException("evento nulo");
        }
        exigir(evento.eventId() != null, "eventId ausente");
        exigir(evento.tipo() != null, "tipo ausente");
        exigir(evento.usuarioId() != null && !evento.usuarioId().isBlank(), "usuarioId ausente");
        switch (evento.tipo()) {
            case JOGO_ADICIONADO -> exigir(evento.statusNovo() != null, "JOGO_ADICIONADO sem statusNovo");
            case STATUS_ALTERADO -> exigir(evento.statusAnterior() != null && evento.statusNovo() != null,
                    "STATUS_ALTERADO sem statusAnterior/statusNovo");
            case JOGO_REMOVIDO -> exigir(evento.statusAnterior() != null, "JOGO_REMOVIDO sem statusAnterior");
        }
    }

    private static void exigir(boolean condicao, String motivo) {
        if (!condicao) {
            throw new EventoInvalidoException(motivo);
        }
    }
}
