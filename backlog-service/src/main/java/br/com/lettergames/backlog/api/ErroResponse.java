package br.com.lettergames.backlog.api;

/** Corpo padrão de erro da API: código estável para máquinas e mensagem para pessoas. */
public record ErroResponse(String erro, String mensagem) {
}
