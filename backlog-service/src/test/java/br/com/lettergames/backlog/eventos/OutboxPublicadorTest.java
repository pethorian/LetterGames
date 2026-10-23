package br.com.lettergames.backlog.eventos;

import br.com.lettergames.contratos.Correlacao;
import br.com.lettergames.contratos.evento.BacklogEvento;
import br.com.lettergames.contratos.evento.StatusBacklog;
import br.com.lettergames.contratos.evento.TipoEvento;
import br.com.lettergames.contratos.evento.Topicos;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.RecordTooLargeException;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
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
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
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
    private KafkaTemplate<String, String> kafkaTemplate;

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
                registrar("joao", TipoEvento.JOGO_ADICIONADO, null, StatusBacklog.QUERO_JOGAR, "demo-01"),
                registrar("joao", TipoEvento.STATUS_ALTERADO, StatusBacklog.QUERO_JOGAR, StatusBacklog.JOGANDO, "demo-02"),
                registrar("joao", TipoEvento.STATUS_ALTERADO, StatusBacklog.JOGANDO, StatusBacklog.ZERADO, "demo-03"));

        int publicados = publicador.publicarPendentes();

        assertThat(publicados).isEqualTo(3);
        List<ConsumerRecord<String, String>> recebidos = consumir("joao", 3);
        assertThat(recebidos).hasSize(3);
        assertThat(recebidos).extracting(ConsumerRecord::partition).as("mesma chave, mesma partição")
                .containsOnly(recebidos.get(0).partition());
        assertThat(eventIds(recebidos)).containsExactlyElementsOf(esperados);

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
    void kafkaForaMantemPendenteIncrementaTentativasEBloqueiaOsSeguintesDaChave() {
        UUID primeiro = registrar("joao", TipoEvento.JOGO_ADICIONADO, null, StatusBacklog.QUERO_JOGAR, "demo-04");
        UUID segundo = registrar("joao", TipoEvento.STATUS_ALTERADO, StatusBacklog.QUERO_JOGAR,
                StatusBacklog.JOGANDO, "demo-05");
        KafkaTemplate<String, String> kafkaFora = kafkaFalso(registro -> new KafkaException("broker fora"));
        OutboxPublicador publicadorSemKafka = new OutboxPublicador(repository, kafkaFora, transacao, 10);

        assertThat(publicadorSemKafka.publicarPendentes()).isZero();
        assertThat(publicadorSemKafka.publicarPendentes()).isZero();

        EventoOutbox linhaPrimeiro = repository.findById(primeiro).orElseThrow();
        EventoOutbox linhaSegundo = repository.findById(segundo).orElseThrow();
        assertThat(linhaPrimeiro.getPublicadoEm()).isNull();
        assertThat(linhaPrimeiro.getTentativas()).isEqualTo(2);
        assertThat(linhaSegundo.getPublicadoEm()).isNull();
        assertThat(linhaSegundo.getTentativas()).as("o seguinte da chave espera o primeiro, preservando a ordem")
                .isZero();
        verify(kafkaFora, times(2)).send(any(ProducerRecord.class));
    }

    @Test
    void falhaPermanenteDeUmaChaveNaoTravaAsOutrasEEstacionaDepoisDoMaximo() {
        UUID x1 = registrar("xavier", TipoEvento.JOGO_ADICIONADO, null, StatusBacklog.QUERO_JOGAR, "demo-06");
        UUID x2 = registrar("xavier", TipoEvento.STATUS_ALTERADO, StatusBacklog.QUERO_JOGAR,
                StatusBacklog.JOGANDO, "demo-07");
        UUID y1 = registrar("yara", TipoEvento.JOGO_ADICIONADO, null, StatusBacklog.QUERO_JOGAR, "demo-08");
        KafkaTemplate<String, String> kafka = kafkaFalso(registro -> "xavier".equals(registro.key())
                ? new RecordTooLargeException("registro rejeitado pelo broker") : null);
        OutboxPublicador publicadorComFalha = new OutboxPublicador(repository, kafka, transacao, 3);

        assertThat(publicadorComFalha.publicarPendentes()).as("yara sai mesmo com xavier falhando").isEqualTo(1);
        assertThat(repository.findById(y1).orElseThrow().getPublicadoEm()).isNotNull();
        EventoOutbox x1AposUmCiclo = repository.findById(x1).orElseThrow();
        assertThat(x1AposUmCiclo.getPublicadoEm()).isNull();
        assertThat(x1AposUmCiclo.getTentativas()).as("uma tentativa por ciclo, mesmo com outras chaves andando")
                .isEqualTo(1);
        assertThat(x1AposUmCiclo.getFalhouEm()).isNull();

        publicadorComFalha.publicarPendentes();
        publicadorComFalha.publicarPendentes();

        EventoOutbox x1Estacionado = repository.findById(x1).orElseThrow();
        assertThat(x1Estacionado.getTentativas()).isEqualTo(3);
        assertThat(x1Estacionado.getFalhouEm()).as("estacionado após max-tentativas").isNotNull();
        assertThat(x1Estacionado.getPublicadoEm()).isNull();

        assertThat(publicadorComFalha.publicarPendentes()).isZero();
        verify(kafka, times(3)).send(argThat((ProducerRecord<String, String> r) -> "xavier".equals(r.key())));
        EventoOutbox x2Retido = repository.findById(x2).orElseThrow();
        assertThat(x2Retido.getPublicadoEm()).as("chave segue bloqueada pelo estacionado").isNull();
        assertThat(x2Retido.getTentativas()).isZero();
    }

    @Test
    void falhaTransitoriaContaTentativaMasNaoEstaciona() {
        UUID evento = registrar("zeca", TipoEvento.JOGO_ADICIONADO, null, StatusBacklog.QUERO_JOGAR, "demo-09");
        KafkaTemplate<String, String> kafkaFora = kafkaFalso(
                registro -> new org.apache.kafka.common.errors.TimeoutException("metadata expirou"));
        OutboxPublicador publicadorSemKafka = new OutboxPublicador(repository, kafkaFora, transacao, 2);

        for (int i = 0; i < 3; i++) {
            publicadorSemKafka.publicarPendentes();
        }

        EventoOutbox linha = repository.findById(evento).orElseThrow();
        assertThat(linha.getTentativas()).isEqualTo(3);
        assertThat(linha.getFalhouEm()).as("queda do broker não estaciona ninguém").isNull();
    }

    /** Prova o at-least-once que justifica o consumidor idempotente (ADR-003). */
    @Test
    void envioConfirmadoComFalhaAoMarcarRepublicaOMesmoEventIdNoCicloSeguinte() throws Exception {
        UUID evento = registrar("ana", TipoEvento.JOGO_ADICIONADO, null, StatusBacklog.QUERO_JOGAR, "demo-10");
        EventoOutboxRepository repositorioQueFalhaUmaVez = mock(EventoOutboxRepository.class, delegatesTo(repository));
        doAnswer(invocacao -> {
            throw new DataAccessResourceFailureException("banco caiu depois do send");
        }).doAnswer(delegatesTo(repository)).when(repositorioQueFalhaUmaVez).marcarPublicado(any(), any());
        OutboxPublicador publicadorInstavel =
                new OutboxPublicador(repositorioQueFalhaUmaVez, kafkaTemplate, transacao, 10);

        assertThat(publicadorInstavel.publicarPendentes()).as("enviado, mas não marcado").isZero();
        assertThat(repository.findById(evento).orElseThrow().getPublicadoEm()).isNull();
        assertThat(publicadorInstavel.publicarPendentes()).isEqualTo(1);

        List<ConsumerRecord<String, String>> recebidos = consumir("ana", 2);
        assertThat(eventIds(recebidos)).as("o mesmo eventId chega duas vezes").containsExactly(evento, evento);
        assertThat(repository.findById(evento).orElseThrow().getPublicadoEm()).isNotNull();
    }

    private UUID registrar(String usuario, TipoEvento tipo, StatusBacklog anterior, StatusBacklog novo,
                           String correlationId) {
        MDC.put(Correlacao.MDC_KEY, correlationId);
        BacklogEvento evento = BacklogEvento.novo(tipo, usuario, 1L, 42L, "Hollow Knight", 27, anterior, novo);
        transacao.executeWithoutResult(status -> registrador.registrar(evento));
        MDC.remove(Correlacao.MDC_KEY);
        return evento.eventId();
    }

    /** KafkaTemplate falso: {@code falha} devolve a exceção do envio daquele registro, ou null para sucesso. */
    @SuppressWarnings("unchecked")
    private static KafkaTemplate<String, String> kafkaFalso(
            Function<ProducerRecord<String, String>, Exception> falha) {
        KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
        when(kafka.send(any(ProducerRecord.class))).thenAnswer(invocacao -> {
            ProducerRecord<String, String> registro = invocacao.getArgument(0);
            Exception erro = falha.apply(registro);
            if (erro != null) {
                return CompletableFuture.failedFuture(erro);
            }
            RecordMetadata metadata = new RecordMetadata(new TopicPartition(registro.topic(), 0), 0, 0, 0, 0, 0);
            return CompletableFuture.completedFuture(new SendResult<>(registro, metadata));
        });
        return kafka;
    }

    /** Lê o tópico desde o início com um group novo e devolve só os registros da chave, em ordem. */
    private List<ConsumerRecord<String, String>> consumir(String chave, int quantidade) {
        Map<String, Object> props = KafkaTestUtils.consumerProps("teste-outbox-" + UUID.randomUUID(), "false", broker);
        List<ConsumerRecord<String, String>> daChave = new ArrayList<>();
        try (Consumer<String, String> consumidor = new DefaultKafkaConsumerFactory<>(props,
                new StringDeserializer(), new StringDeserializer()).createConsumer()) {
            broker.consumeFromAnEmbeddedTopic(consumidor, Topicos.BACKLOG_EVENTOS);
            long limite = System.nanoTime() + Duration.ofSeconds(15).toNanos();
            while (daChave.size() < quantidade && System.nanoTime() < limite) {
                consumidor.poll(Duration.ofMillis(500)).records(Topicos.BACKLOG_EVENTOS).forEach(registro -> {
                    if (chave.equals(registro.key())) {
                        daChave.add(registro);
                    }
                });
            }
        }
        return daChave;
    }

    private List<UUID> eventIds(List<ConsumerRecord<String, String>> registros) throws Exception {
        List<UUID> ids = new ArrayList<>();
        for (ConsumerRecord<String, String> registro : registros) {
            ids.add(objectMapper.readValue(registro.value(), BacklogEvento.class).eventId());
        }
        return ids;
    }

    private static String header(ConsumerRecord<String, String> registro, String nome) {
        return new String(registro.headers().lastHeader(nome).value(), StandardCharsets.UTF_8);
    }
}
