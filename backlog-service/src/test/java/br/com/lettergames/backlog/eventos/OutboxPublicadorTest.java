package br.com.lettergames.backlog.eventos;

import br.com.lettergames.contratos.Correlacao;
import br.com.lettergames.contratos.evento.BacklogEvento;
import br.com.lettergames.contratos.evento.StatusBacklog;
import br.com.lettergames.contratos.evento.TipoEvento;
import br.com.lettergames.contratos.evento.Topicos;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}")
@ActiveProfiles("test")
@EmbeddedKafka(partitions = 3, topics = Topicos.BACKLOG_EVENTOS)
class OutboxPublicadorTest {

    @Autowired
    private OutboxPublicador publicador;

    @Autowired
    private OutboxRegistrador registrador;

    @Autowired
    private EventoOutboxRepository repository;

    @Autowired
    private TransactionTemplate transacao;

    @Autowired
    private EmbeddedKafkaBroker broker;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void limpar() {
        repository.deleteAll();
    }

    @AfterEach
    void limparMdc() {
        MDC.clear();
    }

    @Test
    void publicaPendentesEmOrdemComChaveEHeadersEMarcaComoPublicados() throws Exception {
        List<UUID> esperados = List.of(
                registrar(TipoEvento.JOGO_ADICIONADO, null, StatusBacklog.QUERO_JOGAR, "demo-01"),
                registrar(TipoEvento.STATUS_ALTERADO, StatusBacklog.QUERO_JOGAR, StatusBacklog.JOGANDO, "demo-02"),
                registrar(TipoEvento.STATUS_ALTERADO, StatusBacklog.JOGANDO, StatusBacklog.ZERADO, "demo-03"));

        int publicados = publicador.publicarPendentes();

        assertThat(publicados).isEqualTo(3);
        List<ConsumerRecord<String, String>> recebidos = consumir(3);
        assertThat(recebidos).extracting(ConsumerRecord::key).containsOnly("joao");
        assertThat(recebidos).extracting(ConsumerRecord::partition).as("mesma chave, mesma partição")
                .containsOnly(recebidos.get(0).partition());
        List<UUID> recebidosEmOrdem = new ArrayList<>();
        for (ConsumerRecord<String, String> registro : recebidos) {
            recebidosEmOrdem.add(objectMapper.readValue(registro.value(), BacklogEvento.class).eventId());
        }
        assertThat(recebidosEmOrdem).containsExactlyElementsOf(esperados);

        ConsumerRecord<String, String> primeiro = recebidos.get(0);
        assertThat(header(primeiro, Topicos.HEADER_CORRELATION_ID)).isEqualTo("demo-01");
        assertThat(header(primeiro, Topicos.HEADER_TIPO)).isEqualTo("JOGO_ADICIONADO");
        assertThat(header(recebidos.get(2), Topicos.HEADER_TIPO)).isEqualTo("STATUS_ALTERADO");

        assertThat(repository.findAll()).allSatisfy(linha -> {
            assertThat(linha.getPublicadoEm()).isNotNull();
            assertThat(linha.getTentativas()).isZero();
        });
        assertThat(publicador.publicarPendentes()).as("nada pendente no ciclo seguinte").isZero();
    }

    @Test
    @SuppressWarnings("unchecked")
    void kafkaForaMantemPendenteIncrementaTentativasEParaOLote() {
        UUID primeiro = registrar(TipoEvento.JOGO_ADICIONADO, null, StatusBacklog.QUERO_JOGAR, "demo-04");
        UUID segundo = registrar(TipoEvento.STATUS_ALTERADO, StatusBacklog.QUERO_JOGAR, StatusBacklog.JOGANDO, "demo-05");
        KafkaTemplate<String, String> kafkaFora = mock(KafkaTemplate.class);
        when(kafkaFora.send(any(org.apache.kafka.clients.producer.ProducerRecord.class)))
                .thenReturn(CompletableFuture.failedFuture(new KafkaException("broker fora")));
        OutboxPublicador publicadorSemKafka = new OutboxPublicador(repository, kafkaFora);

        assertThat(publicadorSemKafka.publicarPendentes()).isZero();
        assertThat(publicadorSemKafka.publicarPendentes()).isZero();

        EventoOutbox linhaPrimeiro = repository.findById(primeiro).orElseThrow();
        EventoOutbox linhaSegundo = repository.findById(segundo).orElseThrow();
        assertThat(linhaPrimeiro.getPublicadoEm()).isNull();
        assertThat(linhaPrimeiro.getTentativas()).isEqualTo(2);
        assertThat(linhaSegundo.getPublicadoEm()).isNull();
        assertThat(linhaSegundo.getTentativas()).as("lote para no primeiro erro, preservando a ordem").isZero();
        verify(kafkaFora, times(2)).send(any(org.apache.kafka.clients.producer.ProducerRecord.class));
    }

    private UUID registrar(TipoEvento tipo, StatusBacklog anterior, StatusBacklog novo, String correlationId) {
        MDC.put(Correlacao.MDC_KEY, correlationId);
        BacklogEvento evento = BacklogEvento.novo(tipo, "joao", 1L, 42L, "Hollow Knight", 27, anterior, novo);
        transacao.executeWithoutResult(status -> registrador.registrar(evento));
        MDC.remove(Correlacao.MDC_KEY);
        return evento.eventId();
    }

    private List<ConsumerRecord<String, String>> consumir(int quantidade) {
        Map<String, Object> props = KafkaTestUtils.consumerProps("teste-outbox", "false", broker);
        try (Consumer<String, String> consumidor = new DefaultKafkaConsumerFactory<>(props,
                new StringDeserializer(), new StringDeserializer()).createConsumer()) {
            broker.consumeFromAnEmbeddedTopic(consumidor, Topicos.BACKLOG_EVENTOS);
            ConsumerRecords<String, String> registros =
                    KafkaTestUtils.getRecords(consumidor, Duration.ofSeconds(15), quantidade);
            List<ConsumerRecord<String, String>> lista = new ArrayList<>();
            registros.records(Topicos.BACKLOG_EVENTOS).forEach(lista::add);
            return lista;
        }
    }

    private static String header(ConsumerRecord<String, String> registro, String nome) {
        return new String(registro.headers().lastHeader(nome).value(), StandardCharsets.UTF_8);
    }
}
