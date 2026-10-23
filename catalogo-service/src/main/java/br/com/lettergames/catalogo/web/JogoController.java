package br.com.lettergames.catalogo.web;

import br.com.lettergames.catalogo.dominio.JogoService;
import br.com.lettergames.contratos.catalogo.JogoDTO;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/jogos")
public class JogoController {

    private final JogoService servico;

    public JogoController(JogoService servico) {
        this.servico = servico;
    }

    @PostMapping
    public ResponseEntity<JogoDTO> criar(@Valid @RequestBody JogoRequisicao r) {
        JogoDTO criado = servico.criar(r.titulo(), r.plataforma(), r.genero(), r.duracaoMediaHoras());
        return ResponseEntity.created(URI.create("/jogos/" + criado.id())).body(criado);
    }

    @GetMapping("/{id}")
    public JogoDTO buscar(@PathVariable Long id) {
        return servico.buscar(id);
    }

    @GetMapping
    public List<JogoDTO> listar(@RequestParam(required = false) String plataforma) {
        return servico.listar(plataforma);
    }

    @PutMapping("/{id}")
    public JogoDTO atualizar(@PathVariable Long id, @Valid @RequestBody JogoRequisicao r) {
        return servico.atualizar(id, r.titulo(), r.plataforma(), r.genero(), r.duracaoMediaHoras());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> remover(@PathVariable Long id) {
        servico.remover(id);
        return ResponseEntity.noContent().build();
    }
}
