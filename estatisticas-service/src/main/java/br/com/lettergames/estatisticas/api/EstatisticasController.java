package br.com.lettergames.estatisticas.api;

import br.com.lettergames.estatisticas.conquista.ConquistaRepository;
import br.com.lettergames.estatisticas.estatistica.EstatisticasUsuarioRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Leitura das projeções. Usuário sem eventos ainda recebe zeros (não 404): é um estado válido. */
@RestController
public class EstatisticasController {

    private final EstatisticasUsuarioRepository estatisticas;
    private final ConquistaRepository conquistas;

    public EstatisticasController(EstatisticasUsuarioRepository estatisticas, ConquistaRepository conquistas) {
        this.estatisticas = estatisticas;
        this.conquistas = conquistas;
    }

    @GetMapping("/usuarios/{usuarioId}/estatisticas")
    public EstatisticasResposta estatisticas(@PathVariable String usuarioId) {
        return estatisticas.findById(usuarioId)
                .map(EstatisticasResposta::de)
                .orElseGet(() -> EstatisticasResposta.vazia(usuarioId));
    }

    @GetMapping("/usuarios/{usuarioId}/conquistas")
    public List<ConquistaResposta> conquistas(@PathVariable String usuarioId) {
        return conquistas.findByIdUsuarioIdOrderByDesbloqueadaEmAscIdCodigoAsc(usuarioId).stream()
                .map(ConquistaResposta::de)
                .toList();
    }
}
