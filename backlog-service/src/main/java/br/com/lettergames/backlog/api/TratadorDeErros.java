package br.com.lettergames.backlog.api;

import br.com.lettergames.backlog.catalogo.CatalogoIndisponivelException;
import br.com.lettergames.backlog.catalogo.CatalogoRequisicaoInvalidaException;
import br.com.lettergames.backlog.catalogo.JogoInexistenteNoCatalogoException;
import br.com.lettergames.backlog.dominio.ItemNaoEncontradoException;
import br.com.lettergames.backlog.dominio.JogoJaNoBacklogException;
import br.com.lettergames.backlog.dominio.TransicaoInvalidaException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.stream.Collectors;

@RestControllerAdvice
public class TratadorDeErros {

    private static final Logger log = LoggerFactory.getLogger(TratadorDeErros.class);

    static final String MENSAGEM_CATALOGO_INDISPONIVEL =
            "Catálogo indisponível no momento, tente novamente em instantes";

    @ExceptionHandler(JogoInexistenteNoCatalogoException.class)
    ResponseEntity<ErroResponse> jogoInexistente(JogoInexistenteNoCatalogoException e) {
        return resposta(HttpStatus.UNPROCESSABLE_ENTITY, "JOGO_INEXISTENTE", e.getMessage());
    }

    @ExceptionHandler(CatalogoIndisponivelException.class)
    ResponseEntity<ErroResponse> catalogoIndisponivel(CatalogoIndisponivelException e) {
        log.warn("✘ 503 catálogo indisponível: {}", e.getMessage());
        return resposta(HttpStatus.SERVICE_UNAVAILABLE, "CATALOGO_INDISPONIVEL", MENSAGEM_CATALOGO_INDISPONIVEL);
    }

    // 502: o upstream recusou um pedido nosso; não é culpa do cliente (400) nem indisponibilidade (503).
    @ExceptionHandler(CatalogoRequisicaoInvalidaException.class)
    ResponseEntity<ErroResponse> catalogoRequisicaoInvalida(CatalogoRequisicaoInvalidaException e) {
        log.error("✘ 502 catálogo recusou a requisição: {}", e.getMessage());
        return resposta(HttpStatus.BAD_GATEWAY, "CATALOGO_REQUISICAO_INVALIDA",
                "O catálogo recusou a consulta do jogo (" + e.getStatus() + "); o problema é nosso, não do pedido");
    }

    @ExceptionHandler(JogoJaNoBacklogException.class)
    ResponseEntity<ErroResponse> jogoJaNoBacklog(JogoJaNoBacklogException e) {
        return resposta(HttpStatus.CONFLICT, "JOGO_JA_NO_BACKLOG", e.getMessage());
    }

    @ExceptionHandler(ItemNaoEncontradoException.class)
    ResponseEntity<ErroResponse> itemNaoEncontrado(ItemNaoEncontradoException e) {
        return resposta(HttpStatus.NOT_FOUND, "ITEM_NAO_ENCONTRADO", e.getMessage());
    }

    @ExceptionHandler(TransicaoInvalidaException.class)
    ResponseEntity<ErroResponse> transicaoInvalida(TransicaoInvalidaException e) {
        return resposta(HttpStatus.UNPROCESSABLE_ENTITY, "TRANSICAO_INVALIDA", e.getMessage());
    }

    // Lock pessimista expirou ou o @Version detectou alteração concorrente: o cliente pode repetir.
    @ExceptionHandler({ObjectOptimisticLockingFailureException.class, PessimisticLockingFailureException.class})
    ResponseEntity<ErroResponse> conflitoConcorrente(RuntimeException e) {
        log.warn("✘ 409 conflito concorrente: {}", e.getClass().getSimpleName());
        return resposta(HttpStatus.CONFLICT, "CONFLITO_CONCORRENTE",
                "O item foi alterado por outra requisição ao mesmo tempo, tente novamente");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErroResponse> corpoInvalido(MethodArgumentNotValidException e) {
        String detalhes = e.getBindingResult().getFieldErrors().stream()
                .map(erro -> erro.getField() + ": " + erro.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return validacao(detalhes);
    }

    // Com restrição em @PathVariable, o Spring valida o método inteiro (inclusive o @Valid do corpo) por aqui.
    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<ErroResponse> parametroInvalido(HandlerMethodValidationException e) {
        String detalhes = e.getParameterValidationResults().stream()
                .flatMap(resultado -> resultado.getResolvableErrors().stream()
                        .map(erro -> (erro instanceof FieldError campo
                                ? campo.getField()
                                : resultado.getMethodParameter().getParameterName())
                                + ": " + erro.getDefaultMessage()))
                .collect(Collectors.joining("; "));
        return validacao(detalhes);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ErroResponse> corpoIlegivel(HttpMessageNotReadableException e) {
        return validacao("corpo da requisição ausente ou com valor inválido");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ErroResponse> tipoInvalido(MethodArgumentTypeMismatchException e) {
        return validacao("valor inválido para '" + e.getName() + "'");
    }

    private static ResponseEntity<ErroResponse> validacao(String mensagem) {
        return resposta(HttpStatus.BAD_REQUEST, "VALIDACAO", mensagem);
    }

    private static ResponseEntity<ErroResponse> resposta(HttpStatus status, String erro, String mensagem) {
        return ResponseEntity.status(status).body(new ErroResponse(erro, mensagem));
    }
}
