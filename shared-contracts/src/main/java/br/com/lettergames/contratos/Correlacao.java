package br.com.lettergames.contratos;

/** Nomes usados para propagar o id de correlação entre serviços (HTTP → MDC → Kafka → MDC). */
public final class Correlacao {

    public static final String HEADER_HTTP = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";

    private Correlacao() {
    }
}
