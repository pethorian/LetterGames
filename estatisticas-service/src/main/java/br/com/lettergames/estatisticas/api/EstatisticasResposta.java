package br.com.lettergames.estatisticas.api;

import br.com.lettergames.contratos.evento.StatusBacklog;
import br.com.lettergames.estatisticas.estatistica.EstatisticasUsuario;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumMap;
import java.util.Map;

public record EstatisticasResposta(
        String usuarioId,
        int total,
        Map<StatusBacklog, Integer> porStatus,
        int horasPendentes,
        int horasZeradas,
        double percentualZerado) {

    public static EstatisticasResposta vazia(String usuarioId) {
        return new EstatisticasResposta(usuarioId, 0, porStatus(0, 0, 0, 0), 0, 0, 0.0);
    }

    public static EstatisticasResposta de(EstatisticasUsuario e) {
        int total = e.total();
        double percentual = total == 0 ? 0.0
                : BigDecimal.valueOf(e.getZerados() * 100L)
                .divide(BigDecimal.valueOf(total), 1, RoundingMode.HALF_UP)
                .doubleValue();
        return new EstatisticasResposta(e.getUsuarioId(), total,
                porStatus(e.getQueroJogar(), e.getJogando(), e.getZerados(), e.getAbandonados()),
                e.getHorasPendentes(), e.getHorasZeradas(), percentual);
    }

    private static Map<StatusBacklog, Integer> porStatus(int quero, int jogando, int zerados, int abandonados) {
        // EnumMap mantém a ordem do enum no JSON.
        var mapa = new EnumMap<StatusBacklog, Integer>(StatusBacklog.class);
        mapa.put(StatusBacklog.QUERO_JOGAR, quero);
        mapa.put(StatusBacklog.JOGANDO, jogando);
        mapa.put(StatusBacklog.ZERADO, zerados);
        mapa.put(StatusBacklog.ABANDONADO, abandonados);
        return mapa;
    }
}
