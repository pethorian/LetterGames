package br.com.lettergames.catalogo;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import au.com.dius.pact.provider.junit5.HttpTestTarget;
import au.com.dius.pact.provider.junit5.PactVerificationContext;
import au.com.dius.pact.provider.junitsupport.Provider;
import au.com.dius.pact.provider.junitsupport.State;
import au.com.dius.pact.provider.junitsupport.loader.PactFolder;
import au.com.dius.pact.provider.spring.junit5.PactVerificationSpringProvider;
import br.com.lettergames.catalogo.dominio.JogoNaoEncontradoException;
import br.com.lettergames.catalogo.dominio.JogoService;
import br.com.lettergames.contratos.catalogo.JogoDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Verifica o pact do consumidor (backlog-service) contra a API real do catálogo.
 * O serviço é mockado: aqui só importa o formato HTTP, não o banco.
 * Só passa quando shared-contracts/pacts/backlog-service-catalogo-service.json existir.
 */
@Provider("catalogo-service")
@PactFolder("../shared-contracts/pacts")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class CatalogoPactProviderTest {

    @LocalServerPort
    int porta;

    @MockitoBean
    JogoService servico;

    @BeforeEach
    void alvo(PactVerificationContext contexto) {
        reset(servico);
        contexto.setTarget(new HttpTestTarget("localhost", porta));
    }

    @TestTemplate
    @ExtendWith(PactVerificationSpringProvider.class)
    void verificaContrato(PactVerificationContext contexto) {
        contexto.verifyInteraction();
    }

    @State("jogo 42 existe")
    void jogo42Existe() {
        when(servico.buscar(42L)).thenReturn(new JogoDTO(42L, "Hades", "PC", "Roguelike", 43));
    }

    @State("jogo 999 não existe")
    void jogo999NaoExiste() {
        when(servico.buscar(999L)).thenThrow(new JogoNaoEncontradoException(999L));
    }
}
