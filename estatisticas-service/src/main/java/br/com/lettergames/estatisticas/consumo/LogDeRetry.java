package br.com.lettergames.estatisticas.consumo;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.listener.RetryListener;
import org.springframework.util.backoff.BackOff;
import org.springframework.util.backoff.BackOffExecution;

/**
 * Loga cada nova tentativa agendada pelo error handler, com a classe da falha e o intervalo até ela.
 *
 * <p>O {@code DefaultErrorHandler} avisa o {@link RetryListener} e só depois pede o próximo intervalo ao
 * {@link BackOff}; por isso o log sai do {@link BackOffExecution} envolvido em {@link #comLog}, que já conhece
 * o intervalo, e a exceção da falha atual chega por {@link #failedDelivery} (mesma thread do consumidor).
 */
public class LogDeRetry implements RetryListener {

    private static final Logger log = LoggerFactory.getLogger(LogDeRetry.class);

    private final String grupo;
    private final ObjectMapper objectMapper;
    private final ThreadLocal<Exception> falhaAtual = new ThreadLocal<>();

    public LogDeRetry(String grupo, ObjectMapper objectMapper) {
        this.grupo = grupo;
        this.objectMapper = objectMapper;
    }

    @Override
    public void failedDelivery(ConsumerRecord<?, ?> registro, Exception excecao, int tentativa) {
        falhaAtual.set(excecao);
    }

    /** Envolve o backoff do registro: cada intervalo concedido vira uma linha {@code ⟳ RETRY n}. */
    public BackOff comLog(BackOff base, ClasseFalha classe, ConsumerRecord<?, ?> registro, Exception primeira) {
        return () -> {
            BackOffExecution execucao = base.start();
            int[] tentativa = {0};
            return () -> {
                long intervalo = execucao.nextBackOff();
                Exception excecao = falhaAtual.get();
                falhaAtual.remove();
                // STOP: não há nova tentativa, o registro vai para a DLT (logado pelo RecuperadorDlt).
                if (intervalo != BackOffExecution.STOP) {
                    logar(registro, classe, ++tentativa[0], intervalo, excecao == null ? primeira : excecao);
                }
                return intervalo;
            };
        };
    }

    private void logar(ConsumerRecord<?, ?> registro, ClasseFalha classe, int tentativa, long intervalo,
                       Exception excecao) {
        CorrelacaoKafka.comCorrelacao(registro, () ->
                log.warn("⟳ RETRY {} ({}) consumidor={} eventId={} próxima em {} ms: {}", tentativa, classe.rotulo(),
                        grupo, EventIds.extrair(objectMapper, registro.value()), intervalo,
                        RecuperadorDlt.motivo(excecao)));
    }
}
