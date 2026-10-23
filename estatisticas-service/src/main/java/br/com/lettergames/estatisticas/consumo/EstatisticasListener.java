package br.com.lettergames.estatisticas.consumo;

import br.com.lettergames.contratos.evento.Topicos;
import br.com.lettergames.estatisticas.estatistica.ProjecaoEstatisticas;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Consumer group {@code estatisticas}: mantém a projeção de contadores e horas. */
@Component
public class EstatisticasListener {

    private final LeitorEventos leitor;
    private final ProjecaoEstatisticas projecao;

    public EstatisticasListener(LeitorEventos leitor, ProjecaoEstatisticas projecao) {
        this.leitor = leitor;
        this.projecao = projecao;
    }

    // Exceção aqui volta para o container: o offset não é commitado e o error handler decide (retry ou DLT).
    @KafkaListener(id = "listener-estatisticas", groupId = Consumidores.ESTATISTICAS, topics = Topicos.BACKLOG_EVENTOS,
            containerFactory = KafkaConfig.FACTORY_ESTATISTICAS)
    public void ouvir(ConsumerRecord<String, String> registro) {
        projecao.processar(leitor.ler(registro.value()));
    }
}
