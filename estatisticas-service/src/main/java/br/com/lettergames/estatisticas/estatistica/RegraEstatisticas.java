package br.com.lettergames.estatisticas.estatistica;

import br.com.lettergames.contratos.evento.BacklogEvento;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Aplica um evento do backlog sobre as estatísticas do usuário.
 *
 * <p>Não conhece as regras de transição do backlog (elas são do outro contexto): apenas tira o item do
 * status anterior e o coloca no novo.
 */
@Component
public class RegraEstatisticas {

    private static final Logger log = LoggerFactory.getLogger(RegraEstatisticas.class);

    public void aplicar(EstatisticasUsuario estatisticas, BacklogEvento evento) {
        int horas = evento.duracaoMediaHoras() == null ? 0 : evento.duracaoMediaHoras();
        boolean travou = switch (evento.tipo()) {
            case JOGO_ADICIONADO -> {
                estatisticas.entrar(evento.statusNovo(), horas);
                yield false;
            }
            case STATUS_ALTERADO -> {
                boolean t = estatisticas.sair(evento.statusAnterior(), horas);
                estatisticas.entrar(evento.statusNovo(), horas);
                yield t;
            }
            case JOGO_REMOVIDO -> estatisticas.sair(evento.statusAnterior(), horas);
        };
        if (travou) {
            log.warn("Contador ficaria negativo para {} ({} saindo de {}): travado em 0. Evento fora de ordem? eventId={}",
                    estatisticas.getUsuarioId(), evento.tipo(), evento.statusAnterior(), evento.eventId());
        }
        estatisticas.marcarAtualizacao(Instant.now());
    }
}
