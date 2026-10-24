package br.com.lettergames.estatisticas.consumo;

import br.com.lettergames.contratos.evento.TipoEvento;
import br.com.lettergames.estatisticas.conquista.ProjecaoConquistas;
import br.com.lettergames.estatisticas.conquista.TipoConquista;
import br.com.lettergames.estatisticas.estatistica.ProjecaoEstatisticas;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static br.com.lettergames.contratos.evento.StatusBacklog.ABANDONADO;
import static br.com.lettergames.contratos.evento.StatusBacklog.QUERO_JOGAR;
import static br.com.lettergames.contratos.evento.StatusBacklog.ZERADO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/** Critério 3: reprocessar o mesmo evento N vezes tem o efeito de uma vez, em cada consumer group. */
@ExtendWith(OutputCaptureExtension.class)
class ReprocessamentoIdempotenteTest extends IntegracaoKafkaBase {

    @Autowired
    ProjecaoEstatisticas projecaoEstatisticas;

    @Autowired
    ProjecaoConquistas projecaoConquistas;

    @Test
    void mesmoRegistroPublicadoDuasVezesTemEfeitoDeUmaVez(CapturedOutput saida) {
        String usuario = usuarioUnico("repro");
        var zerado = evento(TipoEvento.JOGO_ADICIONADO, usuario, 30, null, ZERADO);
        // Sentinela na mesma partição (mesma key): quando ele aparece, as duas cópias anteriores já foram consumidas.
        var sentinela = evento(TipoEvento.JOGO_ADICIONADO, usuario, 5, null, ABANDONADO);

        publicar(zerado, "repro-01");
        publicar(zerado, "repro-01");
        publicar(sentinela, "repro-02");

        await().atMost(ESPERA).untilAsserted(() -> {
            assertThat(estatisticasDe(usuario)).hasValueSatisfying(e -> assertThat(e.getAbandonados()).isEqualTo(1));
            assertThat(progressoDe(usuario)).hasValueSatisfying(p -> assertThat(p.getAbandonos()).isEqualTo(1));
        });

        var est = estatisticasDe(usuario).orElseThrow();
        assertThat(est.getZerados()).isEqualTo(1);
        assertThat(est.getHorasZeradas()).isEqualTo(30);
        assertThat(est.total()).isEqualTo(2);

        var progresso = progressoDe(usuario).orElseThrow();
        assertThat(progresso.getZeramentos()).isEqualTo(1);
        assertThat(progresso.getHorasZeradas()).isEqualTo(30);
        assertThat(conquistasDe(usuario))
                .containsExactlyInAnyOrder(TipoConquista.PRIMEIRO_ZERADO, TipoConquista.DESAPEGADO);

        assertThat(processados(zerado.eventId(), Consumidores.ESTATISTICAS)).isEqualTo(1);
        assertThat(processados(zerado.eventId(), Consumidores.CONQUISTAS)).isEqualTo(1);

        assertThat(semCores(saida))
                .contains("↺ DUPLICADO ignorado consumidor=estatisticas eventId=" + zerado.eventId())
                .contains("↺ DUPLICADO ignorado consumidor=conquistas eventId=" + zerado.eventId());
    }

    @Test
    void servicoChamadoDuasVezesDiretoTemEfeitoDeUmaVez() {
        String usuario = usuarioUnico("direto");
        var zerado = evento(TipoEvento.JOGO_ADICIONADO, usuario, 120, null, ZERADO);

        assertThat(projecaoEstatisticas.processar(zerado)).isTrue();
        assertThat(projecaoEstatisticas.processar(zerado)).isFalse();
        assertThat(projecaoConquistas.processar(zerado))
                .containsExactly(TipoConquista.PRIMEIRO_ZERADO, TipoConquista.MARATONISTA);
        assertThat(projecaoConquistas.processar(zerado)).isEmpty();

        var est = estatisticasDe(usuario).orElseThrow();
        assertThat(est.getZerados()).isEqualTo(1);
        assertThat(est.getHorasZeradas()).isEqualTo(120);
        assertThat(progressoDe(usuario).orElseThrow().getZeramentos()).isEqualTo(1);
        assertThat(conquistasDe(usuario)).hasSize(2);
        assertThat(processados(zerado.eventId(), Consumidores.ESTATISTICAS)).isEqualTo(1);
        assertThat(processados(zerado.eventId(), Consumidores.CONQUISTAS)).isEqualTo(1);
    }

    @Test
    void duplicataNaoAdjacenteTemEfeitoDeUmaVez() {
        String usuario = usuarioUnico("nao-adj");
        var zerado = evento(TipoEvento.JOGO_ADICIONADO, usuario, 30, null, ZERADO);
        var abandonado = evento(TipoEvento.JOGO_ADICIONADO, usuario, 5, null, ABANDONADO);
        var sentinela = evento(TipoEvento.JOGO_ADICIONADO, usuario, 1, null, QUERO_JOGAR);

        // A, B, A de novo (com outro evento no meio) e o sentinela C, todos na mesma partição.
        publicar(zerado, "nao-adj-01");
        publicar(abandonado, "nao-adj-02");
        publicar(zerado, "nao-adj-01");
        publicar(sentinela, "nao-adj-03");

        await().atMost(ESPERA).untilAsserted(() -> {
            assertThat(estatisticasDe(usuario)).hasValueSatisfying(e -> assertThat(e.getQueroJogar()).isEqualTo(1));
            assertThat(processados(sentinela.eventId(), Consumidores.CONQUISTAS)).isEqualTo(1);
        });

        var est = estatisticasDe(usuario).orElseThrow();
        assertThat(est.getZerados()).isEqualTo(1);
        assertThat(est.getHorasZeradas()).isEqualTo(30);
        assertThat(est.getAbandonados()).isEqualTo(1);
        assertThat(est.total()).isEqualTo(3);
        assertThat(progressoDe(usuario).orElseThrow().getZeramentos()).isEqualTo(1);
        assertThat(processados(zerado.eventId(), Consumidores.ESTATISTICAS)).isEqualTo(1);
        assertThat(processados(zerado.eventId(), Consumidores.CONQUISTAS)).isEqualTo(1);
    }

    private enum Resultado { APLICOU, DUPLICADO, FALHOU }

    @Test
    void duasEntregasSimultaneasDoMesmoEventoAplicamUmaVez() throws Exception {
        String usuario = usuarioUnico("corrida");
        var zerado = evento(TipoEvento.JOGO_ADICIONADO, usuario, 30, null, ZERADO);
        // Segura quem passar primeiro dentro da transação (eventos_processados gravado, sem commit) para a outra
        // thread chegar no meio. Qualquer intercalação precisa terminar com 1 efeito.
        doAnswer(inv -> {
            Thread.sleep(200);
            return inv.callRealMethod();
        }).when(regra).aplicar(any(), argThat(e -> e != null && zerado.eventId().equals(e.eventId())));

        var largada = new CountDownLatch(1);
        Callable<Resultado> entrega = () -> {
            largada.await();
            try {
                return projecaoEstatisticas.processar(zerado) ? Resultado.APLICOU : Resultado.DUPLICADO;
            } catch (RuntimeException e) {
                // Violação de PK na corrida: rollback; no listener, o error handler reentregaria o registro.
                return Resultado.FALHOU;
            }
        };
        List<Resultado> resultados;
        ExecutorService threads = Executors.newFixedThreadPool(2);
        try {
            Future<Resultado> primeira = threads.submit(entrega);
            Future<Resultado> segunda = threads.submit(entrega);
            largada.countDown();
            resultados = List.of(primeira.get(20, TimeUnit.SECONDS), segunda.get(20, TimeUnit.SECONDS));
        } finally {
            threads.shutdownNow();
        }

        assertThat(resultados).filteredOn(r -> r == Resultado.APLICOU).hasSize(1);
        // A perdedora é duplicada direto, ou falhou e, reentregue após o commit da vencedora, vira duplicada.
        for (Resultado r : resultados) {
            if (r == Resultado.FALHOU) {
                assertThat(projecaoEstatisticas.processar(zerado)).isFalse();
            }
        }

        verify(regra, times(1)).aplicar(any(), argThat(e -> e != null && zerado.eventId().equals(e.eventId())));
        assertThat(processados(zerado.eventId(), Consumidores.ESTATISTICAS)).isEqualTo(1);
        var est = estatisticasDe(usuario).orElseThrow();
        assertThat(est.getZerados()).isEqualTo(1);
        assertThat(est.getHorasZeradas()).isEqualTo(30);
    }
}
