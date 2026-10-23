package br.com.lettergames.backlog.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record AdicionarItemRequest(@NotNull @Positive Long jogoId) {
}
