package br.com.lettergames.catalogo.dominio;

import br.com.lettergames.contratos.catalogo.JogoDTO;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JogoService {

    private static final Logger log = LoggerFactory.getLogger(JogoService.class);

    private final JogoRepository repositorio;

    public JogoService(JogoRepository repositorio) {
        this.repositorio = repositorio;
    }

    @Transactional
    public JogoDTO criar(String titulo, String plataforma, String genero, Integer duracaoMediaHoras) {
        Jogo jogo = repositorio.save(new Jogo(titulo, plataforma, genero, duracaoMediaHoras));
        log.info("Jogo {} criado: {}", jogo.getId(), jogo.getTitulo());
        return jogo.paraDto();
    }

    @Transactional(readOnly = true)
    public JogoDTO buscar(Long id) {
        return repositorio.findById(id).orElseThrow(() -> new JogoNaoEncontradoException(id)).paraDto();
    }

    // Listagem não é cacheada (ADR-004): invalidar listas é difícil e não é o caminho quente.
    @Transactional(readOnly = true)
    public List<JogoDTO> listar(String plataforma) {
        List<Jogo> jogos = (plataforma == null || plataforma.isBlank())
                ? repositorio.findAll()
                : repositorio.findByPlataformaIgnoreCaseOrderById(plataforma);
        return jogos.stream().map(Jogo::paraDto).toList();
    }

    @Transactional
    public JogoDTO atualizar(Long id, String titulo, String plataforma, String genero, Integer duracaoMediaHoras) {
        Jogo jogo = repositorio.findById(id).orElseThrow(() -> new JogoNaoEncontradoException(id));
        jogo.preencher(titulo, plataforma, genero, duracaoMediaHoras);
        return repositorio.save(jogo).paraDto();
    }

    @Transactional
    public void remover(Long id) {
        if (!repositorio.existsById(id)) {
            throw new JogoNaoEncontradoException(id);
        }
        repositorio.deleteById(id);
    }
}
