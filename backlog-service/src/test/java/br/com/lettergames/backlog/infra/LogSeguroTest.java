package br.com.lettergames.backlog.infra;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LogSeguroTest {

    @Test
    void removeQuebrasDeLinhaEControlesQuePoderiamForjarUmaLinhaDeLog() {
        String titulo = "Celeste\r\n12:00:00.000 [demo-01] INFO  BacklogService - ✚ ITEM forjado\u001B[31m fim";

        assertThat(LogSeguro.limpar(titulo))
                .isEqualTo("Celeste12:00:00.000 [demo-01] INFO  BacklogService - ✚ ITEM forjado[31mfim")
                .doesNotContain("\n", "\r", "\u001B", " ");
    }

    @Test
    void preservaTextoComumAcentosENulo() {
        assertThat(LogSeguro.limpar("Ōkami HD: edição \"Ω\"")).isEqualTo("Ōkami HD: edição \"Ω\"");
        assertThat(LogSeguro.limpar(null)).isNull();
    }
}
