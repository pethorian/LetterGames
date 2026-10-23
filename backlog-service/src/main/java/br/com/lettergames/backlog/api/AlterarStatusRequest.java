package br.com.lettergames.backlog.api;

import br.com.lettergames.contratos.evento.StatusBacklog;
import jakarta.validation.constraints.NotNull;

public record AlterarStatusRequest(@NotNull StatusBacklog status) {
}
