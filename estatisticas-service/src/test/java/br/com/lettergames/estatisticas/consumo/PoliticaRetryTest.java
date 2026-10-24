package br.com.lettergames.estatisticas.consumo;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ListenerExecutionFailedException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.util.backoff.BackOff;
import org.springframework.util.backoff.BackOffExecution;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.apache.kafka.clients.consumer.ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Classificação das falhas e valores de backoff de produção (sem Kafka). */
class PoliticaRetryTest {

    private static final PropriedadesRetry PADRAO = new PropriedadesRetry(
            new PropriedadesRetry.Transitoria(Duration.ofSeconds(1), 2, Duration.ofSeconds(30), Duration.ofMinutes(10)),
            new PropriedadesRetry.Processamento(Duration.ofSeconds(1), 2, 3));

    private static ListenerExecutionFailedException doContainer(Throwable causa) {
        return new ListenerExecutionFailedException("listener falhou", causa);
    }

    private static List<Long> intervalos(BackOff backOff) {
        BackOffExecution execucao = backOff.start();
        List<Long> intervalos = new ArrayList<>();
        for (long i = execucao.nextBackOff(); i != BackOffExecution.STOP && intervalos.size() < 1000;
             i = execucao.nextBackOff()) {
            intervalos.add(i);
        }
        return intervalos;
    }

    @Test
    void infraestruturaEmbrulhadaPeloContainerETransitoria() {
        assertThat(List.of(
                new CannotCreateTransactionException("sem conexão"),
                new CannotGetJdbcConnectionException("pool esgotado"),
                new DataAccessResourceFailureException("banco caiu"),
                new ObjectOptimisticLockingFailureException("EstatisticasUsuario", "joao"),
                new PessimisticLockingFailureException("lock"),
                new RuntimeException("wrapper", new java.sql.SQLTransientConnectionException("timeout"))))
                .allSatisfy(causa -> assertThat(ClassificadorFalhas.classificar(doContainer(causa)))
                        .isEqualTo(ClasseFalha.TRANSITORIA));
    }

    @Test
    void bugOuRegraEProcessamento() {
        assertThat(ClassificadorFalhas.classificar(doContainer(new IllegalStateException("bug"))))
                .isEqualTo(ClasseFalha.PROCESSAMENTO);
        assertThat(ClassificadorFalhas.classificar(doContainer(new DataIntegrityViolationException("PK"))))
                .isEqualTo(ClasseFalha.PROCESSAMENTO);
    }

    @Test
    void eventoInvalidoGanhaDeQualquerOutraCausa() {
        var invalido = new EventoInvalidoException("JSON inválido", new CannotCreateTransactionException("x"));
        assertThat(ClassificadorFalhas.classificar(doContainer(invalido))).isEqualTo(ClasseFalha.INVALIDA);
    }

    @Test
    void transitoriaCresceAte30sESegueAteDezMinutos() {
        List<Long> intervalos = intervalos(KafkaConfig.backOffTransitoria(PADRAO));

        assertThat(intervalos).startsWith(1_000L, 2_000L, 4_000L, 8_000L, 16_000L, 30_000L, 30_000L);
        assertThat(intervalos).allSatisfy(i -> assertThat(i).isLessThanOrEqualTo(30_000L));
        long total = intervalos.stream().mapToLong(Long::longValue).sum();
        assertThat(total).isBetween(Duration.ofMinutes(10).toMillis(), Duration.ofMinutes(10).plusSeconds(30).toMillis());
    }

    @Test
    void processamentoTemQuatroTentativas() {
        assertThat(intervalos(KafkaConfig.backOffProcessamento(PADRAO))).containsExactly(1_000L, 2_000L, 4_000L);
    }

    @Test
    void intervaloMaximoPrecisaCaberNoMaxPollInterval() {
        assertThatCode(() -> KafkaConfig.validarMaxPollInterval(new DefaultKafkaConsumerFactory<>(Map.of()), PADRAO))
                .doesNotThrowAnyException();
        var apertado = new DefaultKafkaConsumerFactory<>(Map.<String, Object>of(MAX_POLL_INTERVAL_MS_CONFIG, 45_000));
        assertThatThrownBy(() -> KafkaConfig.validarMaxPollInterval(apertado, PADRAO))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("max.poll.interval.ms");
    }
}
