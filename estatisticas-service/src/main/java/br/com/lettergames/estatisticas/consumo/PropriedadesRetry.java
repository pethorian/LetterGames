package br.com.lettergames.estatisticas.consumo;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Backoff do retry bloqueante do consumidor (ADR-005), por classe de falha.
 *
 * <ul>
 *   <li>{@code transitoria}: 1s ×2 até 30s por intervalo, por até 10 min no total; depois, DLT.</li>
 *   <li>{@code processamento}: 1s, 2s, 4s → 4 tentativas no total; depois, DLT.</li>
 * </ul>
 */
@ConfigurationProperties("estatisticas.retry")
public record PropriedadesRetry(@DefaultValue Transitoria transitoria, @DefaultValue Processamento processamento) {

    public record Transitoria(
            @DefaultValue("1s") Duration intervaloInicial,
            @DefaultValue("2") double multiplicador,
            @DefaultValue("30s") Duration intervaloMaximo,
            @DefaultValue("10m") Duration tempoMaximo) {
    }

    public record Processamento(
            @DefaultValue("1s") Duration intervaloInicial,
            @DefaultValue("2") double multiplicador,
            @DefaultValue("3") int maxTentativasExtras) {
    }
}
