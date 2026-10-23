package br.com.lettergames.backlog.infra;

import java.util.regex.Pattern;

/** Limpa texto de fora (ex.: título vindo do catálogo) antes de ir para o log, evitando forjar linhas. */
public final class LogSeguro {

    // Controles (\n, \r, ESC...) e separadores de linha/parágrafo Unicode.
    private static final Pattern INSEGURO = Pattern.compile("[\\p{Cc}\\p{Zl}\\p{Zp}]");

    private LogSeguro() {
    }

    public static String limpar(String texto) {
        return texto == null ? null : INSEGURO.matcher(texto).replaceAll("");
    }
}
