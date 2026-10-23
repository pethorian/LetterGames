package br.com.lettergames.contratos.evento;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BacklogEventoJsonTest {

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void serializaDataComoTextoIsoEIdaEVoltaPreservaOEvento() throws Exception {
        BacklogEvento evento = BacklogEvento.novo(TipoEvento.STATUS_ALTERADO, "joao", 17L, 42L, "Hollow Knight", 27,
                StatusBacklog.JOGANDO, StatusBacklog.ZERADO);

        String json = mapper.writeValueAsString(evento);

        assertThat(json).contains("\"ocorridoEm\":\"" + evento.ocorridoEm() + "\"");
        assertThat(mapper.readValue(json, BacklogEvento.class)).isEqualTo(evento);
    }

    @Test
    void ignoraCamposDesconhecidosDeVersoesFuturas() throws Exception {
        String json = """
                {"eventId":"6f1c2b9e-1a2b-4c3d-9e8f-001122334455","tipo":"JOGO_ADICIONADO","versao":2,
                 "ocorridoEm":"2026-10-24T14:03:11Z","usuarioId":"joao","itemId":1,"jogoId":42,
                 "titulo":"Hollow Knight","duracaoMediaHoras":27,"statusNovo":"QUERO_JOGAR","campoNovo":"x"}
                """;

        BacklogEvento evento = mapper.readValue(json, BacklogEvento.class);

        assertThat(evento.versao()).isEqualTo(2);
        assertThat(evento.statusAnterior()).isNull();
    }
}
