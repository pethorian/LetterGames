package br.com.lettergames.backlog.dominio;

import br.com.lettergames.contratos.evento.StatusBacklog;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static br.com.lettergames.contratos.evento.StatusBacklog.ABANDONADO;
import static br.com.lettergames.contratos.evento.StatusBacklog.JOGANDO;
import static br.com.lettergames.contratos.evento.StatusBacklog.QUERO_JOGAR;
import static br.com.lettergames.contratos.evento.StatusBacklog.ZERADO;

/**
 * Regras de transição de status do backlog. Ficam aqui (e não no enum do contrato) porque são
 * regra do contexto Backlog: o serviço de estatísticas não precisa conhecê-las.
 */
public final class TransicoesStatus {

    private static final Map<StatusBacklog, Set<StatusBacklog>> PERMITIDAS = new EnumMap<>(StatusBacklog.class);

    static {
        PERMITIDAS.put(QUERO_JOGAR, EnumSet.of(JOGANDO, ABANDONADO));
        PERMITIDAS.put(JOGANDO, EnumSet.of(ZERADO, ABANDONADO, QUERO_JOGAR));
        PERMITIDAS.put(ABANDONADO, EnumSet.of(JOGANDO, QUERO_JOGAR));
        PERMITIDAS.put(ZERADO, EnumSet.of(JOGANDO)); // rejogar
    }

    private TransicoesStatus() {
    }

    public static boolean permite(StatusBacklog de, StatusBacklog para) {
        return PERMITIDAS.get(de).contains(para);
    }

    public static Set<StatusBacklog> destinosPermitidos(StatusBacklog de) {
        return EnumSet.copyOf(PERMITIDAS.get(de));
    }

    /** @throws TransicaoInvalidaException se a transição não for permitida (inclui manter o mesmo status). */
    public static void validar(StatusBacklog de, StatusBacklog para) {
        if (de == para) {
            throw new TransicaoInvalidaException("O item já está em " + para);
        }
        if (!permite(de, para)) {
            throw new TransicaoInvalidaException("Transição inválida: " + de + " → " + para
                    + ". A partir de " + de + " só é permitido: " + destinosPermitidos(de));
        }
    }
}
