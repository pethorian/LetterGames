package br.com.lettergames.backlog.eventos;

import au.com.dius.pact.provider.MessageAndMetadata;
import au.com.dius.pact.provider.PactVerifyProvider;
import au.com.dius.pact.provider.junit5.MessageTestTarget;
import au.com.dius.pact.provider.junit5.PactVerificationContext;
import au.com.dius.pact.provider.junit5.PactVerificationInvocationContextProvider;
import au.com.dius.pact.provider.junitsupport.Provider;
import au.com.dius.pact.provider.junitsupport.loader.PactFolder;
import br.com.lettergames.backlog.catalogo.CatalogoClient;
import br.com.lettergames.backlog.dominio.BacklogService;
import br.com.lettergames.backlog.dominio.ItemBacklogRepository;
import br.com.lettergames.contratos.catalogo.JogoDTO;
import br.com.lettergames.contratos.evento.StatusBacklog;
import br.com.lettergames.contratos.evento.TipoEvento;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.mockito.Mockito.when;

/**
 * Verifica o pact de mensagens do estatisticas-service (consumidor) contra o que o backlog realmente publica:
 * cada mensagem sai do {@link BacklogService} real (catálogo mockado) e é lida do outbox, onde o
 * {@link OutboxRegistrador} gravou o payload exatamente como o {@link OutboxPublicador} o envia ao Kafka.
 */
@Provider("backlog-service")
@PactFolder("../shared-contracts/pacts")
@SpringBootTest
@ActiveProfiles("test")
class BacklogEventoPactProviderTest {

    private static final Map<String, Object> METADADOS = Map.of("contentType", "application/json");

    @Autowired
    private BacklogService service;

    @Autowired
    private ItemBacklogRepository itens;

    @Autowired
    private EventoOutboxRepository outbox;

    @MockitoBean
    private CatalogoClient catalogo;

    @BeforeEach
    void preparar(PactVerificationContext contexto) {
        contexto.setTarget(new MessageTestTarget());
        outbox.deleteAll();
        itens.deleteAll();
        when(catalogo.buscarJogo(42L)).thenReturn(new JogoDTO(42L, "Hollow Knight", "PC", "Metroidvania", 27));
    }

    @TestTemplate
    @ExtendWith(PactVerificationInvocationContextProvider.class)
    void verificaContrato(PactVerificationContext contexto) {
        contexto.verifyInteraction();
    }

    @PactVerifyProvider("um evento JOGO_ADICIONADO")
    MessageAndMetadata jogoAdicionado() {
        service.adicionar("joao", 42L);
        return publicado(TipoEvento.JOGO_ADICIONADO);
    }

    @PactVerifyProvider("um evento STATUS_ALTERADO")
    MessageAndMetadata statusAlterado() {
        long itemId = service.adicionar("joao", 42L).getId();
        service.alterarStatus("joao", itemId, StatusBacklog.JOGANDO);
        return publicado(TipoEvento.STATUS_ALTERADO);
    }

    @PactVerifyProvider("um evento JOGO_REMOVIDO")
    MessageAndMetadata jogoRemovido() {
        long itemId = service.adicionar("joao", 42L).getId();
        service.alterarStatus("joao", itemId, StatusBacklog.JOGANDO);
        service.remover("joao", itemId);
        return publicado(TipoEvento.JOGO_REMOVIDO);
    }

    private MessageAndMetadata publicado(TipoEvento tipo) {
        String payload = outbox.findAll().stream()
                .filter(linha -> linha.getTipo() == tipo)
                .findFirst().orElseThrow().getPayload();
        return new MessageAndMetadata(payload.getBytes(StandardCharsets.UTF_8), METADADOS);
    }
}
