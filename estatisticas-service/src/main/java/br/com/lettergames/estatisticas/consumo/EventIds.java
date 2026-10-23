package br.com.lettergames.estatisticas.consumo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Extrai o eventId de um value JSON só para fins de log, sem lançar exceção. */
final class EventIds {

    private EventIds() {
    }

    static String extrair(ObjectMapper objectMapper, Object value) {
        if (!(value instanceof String json)) {
            return "?";
        }
        try {
            JsonNode id = objectMapper.readTree(json).path("eventId");
            return id.isTextual() ? id.asText() : "?";
        } catch (Exception e) {
            return "?";
        }
    }
}
