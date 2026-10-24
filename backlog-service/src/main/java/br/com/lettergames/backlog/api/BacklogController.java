package br.com.lettergames.backlog.api;

import br.com.lettergames.backlog.dominio.BacklogService;
import br.com.lettergames.backlog.dominio.ItemBacklog;
import br.com.lettergames.contratos.evento.StatusBacklog;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/usuarios/{usuarioId}/backlog")
public class BacklogController {

    // Mesmo formato aceito no X-Correlation-Id: vai para logs e para a chave Kafka, então nada de controle.
    static final String USUARIO_ID = "[A-Za-z0-9._-]{1,64}";

    private final BacklogService service;

    public BacklogController(BacklogService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<ItemBacklogResponse> adicionar(
            @PathVariable @Pattern(regexp = USUARIO_ID) String usuarioId,
            @RequestBody @Valid AdicionarItemRequest request) {
        ItemBacklog item = service.adicionar(usuarioId, request.jogoId());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{itemId}").buildAndExpand(item.getId()).toUri();
        return ResponseEntity.created(location).body(ItemBacklogResponse.de(item));
    }

    @PatchMapping("/{itemId}")
    public ItemBacklogResponse alterarStatus(@PathVariable @Pattern(regexp = USUARIO_ID) String usuarioId,
                                             @PathVariable Long itemId,
                                             @RequestBody @Valid AlterarStatusRequest request) {
        return ItemBacklogResponse.de(service.alterarStatus(usuarioId, itemId, request.status()));
    }

    @GetMapping
    public List<ItemBacklogResponse> listar(@PathVariable @Pattern(regexp = USUARIO_ID) String usuarioId,
                                            @RequestParam(required = false) StatusBacklog status) {
        return service.listar(usuarioId, status).stream().map(ItemBacklogResponse::de).toList();
    }

    @DeleteMapping("/{itemId}")
    public ResponseEntity<Void> remover(@PathVariable @Pattern(regexp = USUARIO_ID) String usuarioId,
                                        @PathVariable Long itemId) {
        service.remover(usuarioId, itemId);
        return ResponseEntity.noContent().build();
    }
}
