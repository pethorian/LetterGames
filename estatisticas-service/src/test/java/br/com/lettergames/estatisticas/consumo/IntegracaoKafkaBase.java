package br.com.lettergames.estatisticas.consumo;

import br.com.lettergames.contratos.evento.BacklogEvento;
import br.com.lettergames.contratos.evento.StatusBacklog;
import br.com.lettergames.contratos.evento.TipoEvento;
import br.com.lettergames.contratos.evento.Topicos;
import br.com.lettergames.estatisticas.conquista.Conquista;
import br.com.lettergames.estatisticas.conquista.ConquistaRepository;
import br.com.lettergames.estatisticas.conquista.ProgressoConquistas;
import br.com.lettergames.estatisticas.conquista.ProgressoConquistasRepository;
import br.com.lettergames.estatisticas.conquista.TipoConquista;
import br.com.lettergames.estatisticas.estatistica.EstatisticasUsuario;
import br.com.lettergames.estatisticas.estatistica.EstatisticasUsuarioRepository;
import br.com.lettergames.estatisticas.estatistica.RegraEstatisticas;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Base dos testes com Kafka embarcado + H2. Todas as subclasses compartilham o mesmo contexto (e o mesmo
 * broker), então cada teste usa um usuarioId próprio (que também é a key do registro).
 *
 * <p>Os spies não alteram o comportamento até um teste programar uma falha; o Spring os reseta ao fim de cada teste.
 */
@SpringBootTest
@ActiveProfiles("test")
@EmbeddedKafka(partitions = 3)
abstract class IntegracaoKafkaBase {

    static final Duration ESPERA = Duration.ofSeconds(30);

    @Autowired
    KafkaTemplate<String, String> kafka;

    @Autowired
    EmbeddedKafkaBroker broker;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    EstatisticasUsuarioRepository estatisticas;

    @Autowired
    ProgressoConquistasRepository progressos;

    @Autowired
    ConquistaRepository conquistas;

    @MockitoSpyBean
    LeitorEventos leitor;

    @MockitoSpyBean
    RegraEstatisticas regra;

    static String usuarioUnico(String prefixo) {
        return prefixo + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    static BacklogEvento evento(TipoEvento tipo, String usuario, int horas, StatusBacklog de, StatusBacklog para) {
        return BacklogEvento.novo(tipo, usuario, 17L, 1L, "Hollow Knight", horas, de, para);
    }

    String json(BacklogEvento evento) {
        try {
            return objectMapper.writeValueAsString(evento);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Publica como o backlog-service: key = usuarioId, value = JSON, headers correlationId e tipo. */
    void publicar(BacklogEvento evento, String correlationId) {
        publicarBruto(evento.usuarioId(), json(evento), correlationId, evento.tipo().name());
    }

    void publicarBruto(String key, String value, String correlationId, String tipo) {
        var registro = new ProducerRecord<String, String>(Topicos.BACKLOG_EVENTOS, key, value);
        registro.headers().add(Topicos.HEADER_CORRELATION_ID, correlationId.getBytes(StandardCharsets.UTF_8));
        if (tipo != null) {
            registro.headers().add(Topicos.HEADER_TIPO, tipo.getBytes(StandardCharsets.UTF_8));
        }
        try {
            kafka.send(registro).get();
        } catch (Exception e) {
            throw new IllegalStateException("falha ao publicar no Kafka embarcado", e);
        }
    }

    Optional<EstatisticasUsuario> estatisticasDe(String usuario) {
        return estatisticas.findById(usuario);
    }

    Optional<ProgressoConquistas> progressoDe(String usuario) {
        return progressos.findById(usuario);
    }

    List<TipoConquista> conquistasDe(String usuario) {
        return conquistas.findByIdUsuarioIdOrderByDesbloqueadaEmAscIdCodigoAsc(usuario).stream()
                .map(Conquista::getTipo).toList();
    }

    int processados(UUID eventId, String consumidor) {
        Integer total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM eventos_processados WHERE event_id = ? AND consumidor = ?",
                Integer.class, eventId, consumidor);
        return total == null ? 0 : total;
    }

    /**
     * Lê a DLT desde o início até encontrar {@code esperados} registros que satisfaçam o filtro, ou até o prazo.
     * Com {@code esperados = 0}, lê por {@code prazo} e devolve o que encontrou (para provar ausência).
     */
    List<ConsumerRecord<String, String>> lerDlt(Predicate<ConsumerRecord<String, String>> filtro, int esperados,
                                                Duration prazo) {
        Map<String, Object> props = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, broker.getBrokersAsString(),
                ConsumerConfig.GROUP_ID_CONFIG, "teste-dlt-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false,
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        List<ConsumerRecord<String, String>> encontrados = new ArrayList<>();
        try (var consumer = new KafkaConsumer<String, String>(props)) {
            consumer.subscribe(List.of(Topicos.BACKLOG_EVENTOS_DLT));
            long limite = System.nanoTime() + prazo.toNanos();
            while (System.nanoTime() < limite && (esperados == 0 || encontrados.size() < esperados)) {
                for (var registro : consumer.poll(Duration.ofMillis(200))) {
                    if (filtro.test(registro)) {
                        encontrados.add(registro);
                    }
                }
            }
        }
        return encontrados;
    }

    /** Saída do console sem os códigos ANSI de cor do padrão de log. */
    static String semCores(CapturedOutput saida) {
        return saida.getOut().replaceAll("\u001B\\[[;\\d]*m", "");
    }

    static String header(ConsumerRecord<?, ?> registro, String nome) {
        Header header = registro.headers().lastHeader(nome);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }
}
