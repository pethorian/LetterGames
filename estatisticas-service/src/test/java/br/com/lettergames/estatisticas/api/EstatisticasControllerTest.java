package br.com.lettergames.estatisticas.api;

import br.com.lettergames.contratos.evento.BacklogEvento;
import br.com.lettergames.contratos.evento.StatusBacklog;
import br.com.lettergames.contratos.evento.TipoEvento;
import br.com.lettergames.estatisticas.conquista.Conquista;
import br.com.lettergames.estatisticas.conquista.ConquistaRepository;
import br.com.lettergames.estatisticas.conquista.TipoConquista;
import br.com.lettergames.estatisticas.estatistica.EstatisticasUsuario;
import br.com.lettergames.estatisticas.estatistica.EstatisticasUsuarioRepository;
import br.com.lettergames.estatisticas.estatistica.RegraEstatisticas;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.hasLength;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EstatisticasController.class)
class EstatisticasControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    EstatisticasUsuarioRepository estatisticas;

    @MockitoBean
    ConquistaRepository conquistas;

    private static EstatisticasUsuario estatisticasDoJoao() {
        var est = new EstatisticasUsuario("joao");
        var regra = new RegraEstatisticas();
        regra.aplicar(est, evento(StatusBacklog.QUERO_JOGAR, 20));
        regra.aplicar(est, evento(StatusBacklog.JOGANDO, 10));
        regra.aplicar(est, evento(StatusBacklog.ZERADO, 27));
        return est;
    }

    private static BacklogEvento evento(StatusBacklog status, int horas) {
        return BacklogEvento.novo(TipoEvento.JOGO_ADICIONADO, "joao", 1L, 1L, "Jogo", horas, null, status);
    }

    @Test
    void retornaEstatisticasComPercentualDeUmaCasa() throws Exception {
        when(estatisticas.findById("joao")).thenReturn(Optional.of(estatisticasDoJoao()));

        mvc.perform(get("/usuarios/joao/estatisticas").header("X-Correlation-Id", "demo-01"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Correlation-Id", "demo-01"))
                .andExpect(jsonPath("$.usuarioId").value("joao"))
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.porStatus.QUERO_JOGAR").value(1))
                .andExpect(jsonPath("$.porStatus.JOGANDO").value(1))
                .andExpect(jsonPath("$.porStatus.ZERADO").value(1))
                .andExpect(jsonPath("$.porStatus.ABANDONADO").value(0))
                .andExpect(jsonPath("$.horasPendentes").value(30))
                .andExpect(jsonPath("$.horasZeradas").value(27))
                .andExpect(jsonPath("$.percentualZerado").value(33.3));
    }

    @Test
    void usuarioSemDadosRecebeZeros() throws Exception {
        when(estatisticas.findById("novato")).thenReturn(Optional.empty());

        mvc.perform(get("/usuarios/novato/estatisticas"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Correlation-Id", hasLength(8)))
                .andExpect(jsonPath("$.usuarioId").value("novato"))
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.porStatus.QUERO_JOGAR").value(0))
                .andExpect(jsonPath("$.porStatus.ZERADO").value(0))
                .andExpect(jsonPath("$.horasPendentes").value(0))
                .andExpect(jsonPath("$.horasZeradas").value(0))
                .andExpect(jsonPath("$.percentualZerado").value(0.0));
    }

    @Test
    void correlationIdForaDoPadraoETrocadoPorUmNovo() throws Exception {
        when(estatisticas.findById("joao")).thenReturn(Optional.empty());

        for (String invalido : new String[] {"demo 01", "x".repeat(65), "demo\tforjado"}) {
            mvc.perform(get("/usuarios/joao/estatisticas").header("X-Correlation-Id", invalido))
                    .andExpect(status().isOk())
                    .andExpect(header().string("X-Correlation-Id", hasLength(8)));
        }
        mvc.perform(get("/usuarios/joao/estatisticas").header("X-Correlation-Id", "a".repeat(64)))
                .andExpect(header().string("X-Correlation-Id", "a".repeat(64)));
    }

    @Test
    void usuarioIdForaDoPadraoRecebe400() throws Exception {
        mvc.perform(get("/usuarios/{id}/estatisticas", "x".repeat(65))).andExpect(status().isBadRequest());
        mvc.perform(get("/usuarios/{id}/estatisticas", "jo$ao")).andExpect(status().isBadRequest());
        mvc.perform(get("/usuarios/{id}/conquistas", "jo ao")).andExpect(status().isBadRequest());
    }

    @Test
    void listaConquistasDesbloqueadas() throws Exception {
        var quando = Instant.parse("2026-10-24T14:03:11.123Z");
        when(conquistas.findByIdUsuarioIdOrderByDesbloqueadaEmAscIdCodigoAsc("joao")).thenReturn(List.of(
                new Conquista("joao", TipoConquista.PRIMEIRO_ZERADO, quando),
                new Conquista("joao", TipoConquista.DESAPEGADO, quando.plusSeconds(60))));

        mvc.perform(get("/usuarios/joao/conquistas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].codigo").value("PRIMEIRO_ZERADO"))
                .andExpect(jsonPath("$[0].descricao").value("Primeiro zerado!"))
                .andExpect(jsonPath("$[0].desbloqueadaEm").value("2026-10-24T14:03:11.123Z"))
                .andExpect(jsonPath("$[1].codigo").value("DESAPEGADO"));
    }

    @Test
    void usuarioSemConquistasRecebeListaVazia() throws Exception {
        when(conquistas.findByIdUsuarioIdOrderByDesbloqueadaEmAscIdCodigoAsc("novato")).thenReturn(List.of());

        mvc.perform(get("/usuarios/novato/conquistas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
