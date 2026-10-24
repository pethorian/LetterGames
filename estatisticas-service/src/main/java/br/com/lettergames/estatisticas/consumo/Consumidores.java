package br.com.lettergames.estatisticas.consumo;

/**
 * Consumer groups deste serviço. Cada group lê o tópico inteiro de forma independente
 * (offsets e idempotência próprios), então uma falha em um não atrasa o outro.
 */
public final class Consumidores {

    public static final String ESTATISTICAS = "estatisticas";
    public static final String CONQUISTAS = "conquistas";

    private Consumidores() {
    }
}
