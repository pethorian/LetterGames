package br.com.lettergames.estatisticas.consumo;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/** Backoff do retry bloqueante do consumidor (ADR-005). Padrão: 1s, 2s, 4s → 4 tentativas no total. */
@ConfigurationProperties("estatisticas.retry")
public record PropriedadesRetry(
        @DefaultValue("1s") Duration intervaloInicial,
        @DefaultValue("2") double multiplicador,
        @DefaultValue("3") int maxTentativasExtras) {

    public int totalTentativas() {
        return maxTentativasExtras + 1;
    }
}
