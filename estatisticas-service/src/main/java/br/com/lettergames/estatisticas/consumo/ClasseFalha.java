package br.com.lettergames.estatisticas.consumo;

/** Como o error handler trata uma falha do listener (ADR-005). */
public enum ClasseFalha {

    /** Registro que nunca vai ser processável: DLT direto, sem retry. */
    INVALIDA("inválida"),
    /** Infraestrutura fora (banco, conexão, lock): backoff longo, segura a partição para não perder a ordem. */
    TRANSITORIA("transitória"),
    /** Bug ou regra: poucas tentativas e DLT. */
    PROCESSAMENTO("processamento");

    private final String rotulo;

    ClasseFalha(String rotulo) {
        this.rotulo = rotulo;
    }

    public String rotulo() {
        return rotulo;
    }
}
