package br.com.lettergames.contratos.evento;

/** Nomes de tópicos e headers Kafka compartilhados entre produtor e consumidores. */
public final class Topicos {

    public static final String BACKLOG_EVENTOS = "backlog.eventos";
    public static final String BACKLOG_EVENTOS_DLT = "backlog.eventos.DLT";

    /** Header Kafka com o id de correlação (propagado a partir do HTTP {@code X-Correlation-Id}). */
    public static final String HEADER_CORRELATION_ID = "correlationId";
    /** Header Kafka com o {@link TipoEvento}, útil para filtrar no Kafka UI. */
    public static final String HEADER_TIPO = "tipo";

    private Topicos() {
    }
}
