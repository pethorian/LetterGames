package br.com.lettergames.estatisticas.consumo;

import br.com.lettergames.contratos.evento.Topicos;
import br.com.lettergames.estatisticas.conquista.ProjecaoConquistas;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumer group {@code conquistas}: lê o mesmo tópico de forma independente do group {@code estatisticas}
 * (offsets, retry e idempotência próprios).
 */
@Component
public class ConquistasListener {

    private final LeitorEventos leitor;
    private final ProjecaoConquistas projecao;

    public ConquistasListener(LeitorEventos leitor, ProjecaoConquistas projecao) {
        this.leitor = leitor;
        this.projecao = projecao;
    }

    @KafkaListener(id = "listener-conquistas", groupId = Consumidores.CONQUISTAS, topics = Topicos.BACKLOG_EVENTOS,
            containerFactory = KafkaConfig.FACTORY_CONQUISTAS)
    public void ouvir(ConsumerRecord<String, String> registro) {
        projecao.processar(leitor.ler(registro.value()));
    }
}
