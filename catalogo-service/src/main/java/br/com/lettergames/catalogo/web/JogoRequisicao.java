package br.com.lettergames.catalogo.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Corpo de POST/PUT /jogos. A resposta é sempre o JogoDTO do shared-contracts.
 * Tamanhos seguem as colunas da V1; o padrão rejeita caracteres de controle (evita injetar linhas falsas nos logs).
 */
public record JogoRequisicao(
        @NotBlank @Size(max = 200) @Pattern(regexp = SEM_CONTROLE, message = "contém caracteres de controle") String titulo,
        @NotBlank @Size(max = 50) @Pattern(regexp = SEM_CONTROLE, message = "contém caracteres de controle") String plataforma,
        @NotBlank @Size(max = 50) @Pattern(regexp = SEM_CONTROLE, message = "contém caracteres de controle") String genero,
        @NotNull @Positive Integer duracaoMediaHoras) {

    /** Nenhum caractere de controle (inclui \n, \r, \t) nem separador de linha/parágrafo Unicode. */
    static final String SEM_CONTROLE = "[^\\p{Cntrl}\\u2028\\u2029]*";
}
