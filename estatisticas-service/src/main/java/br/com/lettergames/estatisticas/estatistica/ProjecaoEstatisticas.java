package br.com.lettergames.estatisticas.estatistica;

import br.com.lettergames.contratos.evento.BacklogEvento;
import br.com.lettergames.estatisticas.consumo.Consumidores;
import br.com.lettergames.estatisticas.consumo.RegistroIdempotencia;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Serviço de aplicação do consumer group {@code estatisticas}. */
@Service
public class ProjecaoEstatisticas {

    private static final Logger log = LoggerFactory.getLogger(ProjecaoEstatisticas.class);

    private final RegistroIdempotencia idempotencia;
    private final EstatisticasUsuarioRepository repositorio;
    private final RegraEstatisticas regra;

    public ProjecaoEstatisticas(RegistroIdempotencia idempotencia, EstatisticasUsuarioRepository repositorio,
                                RegraEstatisticas regra) {
        this.idempotencia = idempotencia;
        this.repositorio = repositorio;
        this.regra = regra;
    }

    /**
     * Registra o evento como processado e aplica o efeito na mesma transação.
     *
     * @return {@code false} se o evento já tinha sido processado por este consumidor
     */
    @Transactional
    public boolean processar(BacklogEvento evento) {
        if (!idempotencia.registrarSeNovo(evento.eventId(), Consumidores.ESTATISTICAS)) {
            return false;
        }
        EstatisticasUsuario estatisticas = repositorio.findById(evento.usuarioId())
                .orElseGet(() -> new EstatisticasUsuario(evento.usuarioId()));
        regra.aplicar(estatisticas, evento);
        repositorio.save(estatisticas);
        log.info("📊 ESTATÍSTICAS {}: quero={} jogando={} zerados={} abandonados={} · {}h pendentes · {}h zeradas",
                estatisticas.getUsuarioId(), estatisticas.getQueroJogar(), estatisticas.getJogando(),
                estatisticas.getZerados(), estatisticas.getAbandonados(), estatisticas.getHorasPendentes(),
                estatisticas.getHorasZeradas());
        return true;
    }
}
