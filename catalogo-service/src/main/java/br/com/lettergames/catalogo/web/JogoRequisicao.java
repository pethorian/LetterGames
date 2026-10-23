package br.com.lettergames.catalogo.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** Corpo de POST/PUT /jogos. A resposta é sempre o JogoDTO do shared-contracts. */
public record JogoRequisicao(
        @NotBlank String titulo,
        @NotBlank String plataforma,
        @NotBlank String genero,
        @NotNull @Positive Integer duracaoMediaHoras) {
}
