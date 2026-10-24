package br.com.lettergames.backlog.dominio;

import br.com.lettergames.contratos.evento.StatusBacklog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Cobre as 16 combinações (de × para) da matriz de transições. */
class TransicoesStatusTest {

    @ParameterizedTest(name = "{0} → {1} permitido={2}")
    @CsvSource({
            "QUERO_JOGAR, QUERO_JOGAR, false",
            "QUERO_JOGAR, JOGANDO,     true",
            "QUERO_JOGAR, ZERADO,      false",
            "QUERO_JOGAR, ABANDONADO,  true",
            "JOGANDO,     QUERO_JOGAR, true",
            "JOGANDO,     JOGANDO,     false",
            "JOGANDO,     ZERADO,      true",
            "JOGANDO,     ABANDONADO,  true",
            "ZERADO,      QUERO_JOGAR, false",
            "ZERADO,      JOGANDO,     true",
            "ZERADO,      ZERADO,      false",
            "ZERADO,      ABANDONADO,  false",
            "ABANDONADO,  QUERO_JOGAR, true",
            "ABANDONADO,  JOGANDO,     true",
            "ABANDONADO,  ZERADO,      false",
            "ABANDONADO,  ABANDONADO,  false"
    })
    void matrizDeTransicoes(StatusBacklog de, StatusBacklog para, boolean permitido) {
        assertThat(TransicoesStatus.permite(de, para)).isEqualTo(permitido);

        if (permitido) {
            TransicoesStatus.validar(de, para);
        } else {
            assertThatThrownBy(() -> TransicoesStatus.validar(de, para))
                    .isInstanceOf(TransicaoInvalidaException.class);
        }
    }

    @Test
    void mesmoStatusTemMensagemPropria() {
        assertThatThrownBy(() -> TransicoesStatus.validar(StatusBacklog.JOGANDO, StatusBacklog.JOGANDO))
                .hasMessage("O item já está em JOGANDO");
    }

    @Test
    void transicaoInvalidaExplicaOsDestinosPermitidos() {
        assertThatThrownBy(() -> TransicoesStatus.validar(StatusBacklog.ZERADO, StatusBacklog.ABANDONADO))
                .hasMessage("Transição inválida: ZERADO → ABANDONADO. A partir de ZERADO só é permitido: [JOGANDO]");
    }

    @Test
    void itemAplicaATransicaoEDevolveOStatusAnterior() {
        ItemBacklog item = ItemBacklog.novo("joao", 42L, "Hollow Knight", 27);
        var criadoEm = item.getAtualizadoEm();

        StatusBacklog anterior = item.alterarStatus(StatusBacklog.JOGANDO);

        assertThat(anterior).isEqualTo(StatusBacklog.QUERO_JOGAR);
        assertThat(item.getStatus()).isEqualTo(StatusBacklog.JOGANDO);
        assertThat(item.getAtualizadoEm()).isAfterOrEqualTo(criadoEm);
    }

    @Test
    void itemNaoMudaQuandoATransicaoEInvalida() {
        ItemBacklog item = ItemBacklog.novo("joao", 42L, "Hollow Knight", 27);

        assertThatThrownBy(() -> item.alterarStatus(StatusBacklog.ZERADO))
                .isInstanceOf(TransicaoInvalidaException.class);
        assertThat(item.getStatus()).isEqualTo(StatusBacklog.QUERO_JOGAR);
    }
}
