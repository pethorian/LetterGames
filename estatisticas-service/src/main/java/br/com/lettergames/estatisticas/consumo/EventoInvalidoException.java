package br.com.lettergames.estatisticas.consumo;

/**
 * Falha permanente: o registro nunca vai ser processável (JSON inválido, campo obrigatório ausente).
 * Não é retentada; vai direto para a DLT para não travar a partição (poison pill).
 */
public class EventoInvalidoException extends RuntimeException {

    public EventoInvalidoException(String mensagem) {
        super(mensagem);
    }

    public EventoInvalidoException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
