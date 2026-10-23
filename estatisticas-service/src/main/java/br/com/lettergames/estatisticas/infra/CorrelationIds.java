package br.com.lettergames.estatisticas.infra;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Regra única para ids externos que vão para o log (correlationId, usuarioId): só caracteres seguros, até 64.
 * Evita injeção de linhas falsas no log (CR/LF) e valores gigantes no MDC.
 */
public final class CorrelationIds {

    public static final String REGEX = "[A-Za-z0-9._-]{1,64}";

    private static final Pattern PADRAO = Pattern.compile(REGEX);

    private CorrelationIds() {
    }

    public static boolean valido(String valor) {
        return valor != null && PADRAO.matcher(valor).matches();
    }

    public static String novo() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
