package br.com.lettergames.estatisticas.consumo;

import au.com.dius.pact.consumer.MessagePactBuilder;
import au.com.dius.pact.consumer.dsl.PactDslJsonBody;
import au.com.dius.pact.consumer.junit5.PactConsumerTestExt;
import au.com.dius.pact.consumer.junit5.PactTestFor;
import au.com.dius.pact.consumer.junit5.ProviderType;
import au.com.dius.pact.core.model.PactSpecVersion;
import au.com.dius.pact.core.model.V4Interaction;
import au.com.dius.pact.core.model.V4Pact;
import au.com.dius.pact.core.model.annotations.Pact;
import br.com.lettergames.contratos.evento.BacklogEvento;
import br.com.lettergames.contratos.evento.StatusBacklog;
import br.com.lettergames.contratos.evento.TipoEvento;
import br.com.lettergames.estatisticas.conquista.ProgressoConquistas;
import br.com.lettergames.estatisticas.conquista.TipoConquista;
import br.com.lettergames.estatisticas.estatistica.EstatisticasUsuario;
import br.com.lettergames.estatisticas.estatistica.RegraEstatisticas;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrato consumer-driven de mensagem backlog → estatísticas (ADR-006). Gera
 * {@code shared-contracts/pacts/estatisticas-service-backlog-service.json}, verificado pelo backlog-service com
 * {@code @PactVerifyProvider} usando as mesmas descrições.
 *
 * <p>Cada mensagem passa pelo código real do consumidor ({@link LeitorEventos}, {@link RegraEstatisticas} e
 * {@link ProgressoConquistas}), sem Spring: prova que o consumidor entende e aplica o que o contrato descreve.
 *
 * <p>Todo matcher tem exemplo fixo: sem exemplo, o Pact gera valor aleatório e o arquivo mudaria a cada build.
 */
@ExtendWith(PactConsumerTestExt.class)
@PactTestFor(providerName = "backlog-service", providerType = ProviderType.ASYNCH, pactVersion = PactSpecVersion.V4)
class BacklogEventoPactConsumerTest {

    private static final String CONSUMIDOR = "estatisticas-service";
    private static final String PROVEDOR = "backlog-service";

    private static final String EVENT_ID = "6f1c2b9e-1a2b-4c3d-9e8f-001122334455";
    private static final String USUARIO = "joao";
    private static final int HORAS = 27;
    // Instant ISO-8601 em UTC, com 0 a 9 casas decimais (o Jackson omite a fração quando é zero).
    private static final String INSTANTE_ISO = "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d{1,9})?Z";
    private static final String STATUS = "QUERO_JOGAR|JOGANDO|ZERADO|ABANDONADO";

    private final LeitorEventos leitor = new LeitorEventos(Jackson2ObjectMapperBuilder.json().build());
    private final RegraEstatisticas regra = new RegraEstatisticas();

    private static PactDslJsonBody corpo(TipoEvento tipo, StatusBacklog anterior, StatusBacklog novo) {
        var corpo = new PactDslJsonBody()
                .uuid("eventId", EVENT_ID)
                .stringValue("tipo", tipo.name())
                .integerType("versao", 1)
                .stringMatcher("ocorridoEm", INSTANTE_ISO, "2026-10-24T14:03:11.123Z")
                .stringType("usuarioId", USUARIO)
                .integerType("itemId", 17)
                .integerType("jogoId", 42)
                .stringType("titulo", "Hollow Knight")
                .integerType("duracaoMediaHoras", HORAS);
        status(corpo, "statusAnterior", anterior);
        status(corpo, "statusNovo", novo);
        return corpo;
    }

    private static void status(PactDslJsonBody corpo, String campo, StatusBacklog valor) {
        if (valor == null) {
            corpo.nullValue(campo);
        } else {
            corpo.stringMatcher(campo, STATUS, valor.name());
        }
    }

    private static V4Pact mensagem(MessagePactBuilder builder, String descricao, PactDslJsonBody corpo) {
        return builder.expectsToReceive(descricao)
                .withMetadata(Map.of("contentType", "application/json"))
                .withContent(corpo)
                .toPact(V4Pact.class);
    }

    @Pact(consumer = CONSUMIDOR, provider = PROVEDOR)
    V4Pact jogoAdicionado(MessagePactBuilder builder) {
        return mensagem(builder, "um evento JOGO_ADICIONADO",
                corpo(TipoEvento.JOGO_ADICIONADO, null, StatusBacklog.QUERO_JOGAR));
    }

    @Pact(consumer = CONSUMIDOR, provider = PROVEDOR)
    V4Pact statusAlterado(MessagePactBuilder builder) {
        return mensagem(builder, "um evento STATUS_ALTERADO",
                corpo(TipoEvento.STATUS_ALTERADO, StatusBacklog.JOGANDO, StatusBacklog.ZERADO));
    }

    @Pact(consumer = CONSUMIDOR, provider = PROVEDOR)
    V4Pact jogoRemovido(MessagePactBuilder builder) {
        return mensagem(builder, "um evento JOGO_REMOVIDO",
                corpo(TipoEvento.JOGO_REMOVIDO, StatusBacklog.ZERADO, null));
    }

    /** Estado anterior em que o item já está no status informado (como se um evento anterior tivesse chegado). */
    private EstatisticasUsuario comItemEm(StatusBacklog status) {
        var estatisticas = new EstatisticasUsuario(USUARIO);
        regra.aplicar(estatisticas, BacklogEvento.novo(TipoEvento.JOGO_ADICIONADO, USUARIO, 17L, 42L,
                "Hollow Knight", HORAS, null, status));
        return estatisticas;
    }

    private BacklogEvento ler(V4Interaction.AsynchronousMessage mensagem) {
        assertThat(mensagem.getMetadata()).containsEntry("contentType", "application/json");
        BacklogEvento evento = leitor.ler(mensagem.contentsAsString());
        assertThat(evento.eventId()).isEqualTo(UUID.fromString(EVENT_ID));
        assertThat(evento.versao()).isEqualTo(BacklogEvento.VERSAO_ATUAL);
        assertThat(evento.usuarioId()).isEqualTo(USUARIO);
        assertThat(evento.ocorridoEm()).isNotNull();
        return evento;
    }

    @Test
    @PactTestFor(pactMethod = "jogoAdicionado")
    void jogoAdicionadoEntraEmQueroJogar(V4Interaction.AsynchronousMessage mensagem) {
        BacklogEvento evento = ler(mensagem);
        var estatisticas = new EstatisticasUsuario(USUARIO);

        regra.aplicar(estatisticas, evento);

        assertThat(evento.tipo()).isEqualTo(TipoEvento.JOGO_ADICIONADO);
        assertThat(estatisticas.getQueroJogar()).isEqualTo(1);
        assertThat(estatisticas.getHorasPendentes()).isEqualTo(HORAS);
        assertThat(new ProgressoConquistas(USUARIO).registrar(evento)).isFalse();
    }

    @Test
    @PactTestFor(pactMethod = "statusAlterado")
    void statusAlteradoMoveDeJogandoParaZeradoEDesbloqueiaConquista(V4Interaction.AsynchronousMessage mensagem) {
        BacklogEvento evento = ler(mensagem);
        var estatisticas = comItemEm(StatusBacklog.JOGANDO);
        var progresso = new ProgressoConquistas(USUARIO);

        regra.aplicar(estatisticas, evento);

        assertThat(estatisticas.getJogando()).isZero();
        assertThat(estatisticas.getZerados()).isEqualTo(1);
        assertThat(estatisticas.getHorasPendentes()).isZero();
        assertThat(estatisticas.getHorasZeradas()).isEqualTo(HORAS);
        assertThat(progresso.registrar(evento)).isTrue();
        assertThat(TipoConquista.PRIMEIRO_ZERADO.atingida(progresso)).isTrue();
    }

    @Test
    @PactTestFor(pactMethod = "jogoRemovido")
    void jogoRemovidoSaiDeZerado(V4Interaction.AsynchronousMessage mensagem) {
        BacklogEvento evento = ler(mensagem);
        var estatisticas = comItemEm(StatusBacklog.ZERADO);

        regra.aplicar(estatisticas, evento);

        assertThat(evento.statusNovo()).isNull();
        assertThat(estatisticas.total()).isZero();
        assertThat(estatisticas.getHorasZeradas()).isZero();
        assertThat(new ProgressoConquistas(USUARIO).registrar(evento)).isFalse();
    }
}
