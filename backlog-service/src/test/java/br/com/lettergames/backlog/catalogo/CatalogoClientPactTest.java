package br.com.lettergames.backlog.catalogo;

import au.com.dius.pact.consumer.MockServer;
import au.com.dius.pact.consumer.dsl.PactDslJsonBody;
import au.com.dius.pact.consumer.dsl.PactDslWithProvider;
import au.com.dius.pact.consumer.junit5.PactConsumerTestExt;
import au.com.dius.pact.consumer.junit5.PactTestFor;
import au.com.dius.pact.core.model.V4Pact;
import au.com.dius.pact.core.model.annotations.Pact;
import br.com.lettergames.contratos.catalogo.JogoDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Contrato consumer-driven backlog → catálogo (ADR-006). Gera
 * {@code shared-contracts/pacts/backlog-service-catalogo-service.json}, verificado pelo catalogo-service.
 * Usa o {@link CatalogoClient} real, sem Spring: o contrato é o HTTP, não a resiliência.
 */
@ExtendWith(PactConsumerTestExt.class)
@PactTestFor(providerName = "catalogo-service")
class CatalogoClientPactTest {

    @Pact(consumer = "backlog-service", provider = "catalogo-service")
    V4Pact jogoExiste(PactDslWithProvider builder) {
        return builder
                .given("jogo 42 existe")
                .uponReceiving("busca do jogo 42")
                .method("GET")
                .path("/jogos/42")
                .headers("Accept", "application/json")
                .willRespondWith()
                .status(200)
                .headers(Map.of("Content-Type", "application/json"))
                .body(new PactDslJsonBody()
                        .integerType("id", 42)
                        .stringType("titulo", "Hollow Knight")
                        .stringType("plataforma", "PC")
                        .stringType("genero", "Metroidvania")
                        .integerType("duracaoMediaHoras", 27))
                .toPact(V4Pact.class);
    }

    @Pact(consumer = "backlog-service", provider = "catalogo-service")
    V4Pact jogoNaoExiste(PactDslWithProvider builder) {
        return builder
                .given("jogo 999 não existe")
                .uponReceiving("busca do jogo 999 inexistente")
                .method("GET")
                .path("/jogos/999")
                .headers("Accept", "application/json")
                .willRespondWith()
                .status(404)
                .toPact(V4Pact.class);
    }

    @Test
    @PactTestFor(pactMethod = "jogoExiste")
    void buscaJogoExistente(MockServer mockServer) {
        JogoDTO jogo = new CatalogoClient(mockServer.getUrl()).buscarJogo(42L);

        assertThat(jogo).isEqualTo(new JogoDTO(42L, "Hollow Knight", "PC", "Metroidvania", 27));
    }

    @Test
    @PactTestFor(pactMethod = "jogoNaoExiste")
    void jogoInexistenteViraExcecaoDeDominio(MockServer mockServer) {
        CatalogoClient client = new CatalogoClient(mockServer.getUrl());

        assertThatThrownBy(() -> client.buscarJogo(999L))
                .isInstanceOf(JogoInexistenteNoCatalogoException.class);
    }
}
