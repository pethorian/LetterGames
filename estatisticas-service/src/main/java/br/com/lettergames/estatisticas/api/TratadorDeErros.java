package br.com.lettergames.estatisticas.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/** Banco das projeções fora do ar vira 503 com uma linha de log, sem stack trace no terminal da demo. */
@RestControllerAdvice
public class TratadorDeErros {

    private static final Logger log = LoggerFactory.getLogger(TratadorDeErros.class);

    @ExceptionHandler({DataAccessException.class, CannotCreateTransactionException.class})
    public ResponseEntity<Map<String, String>> bancoIndisponivel(RuntimeException e) {
        log.warn("✘ 503 banco de estatísticas indisponível: {}", e.getClass().getSimpleName());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                "erro", "BANCO_INDISPONIVEL",
                "mensagem", "Estatísticas indisponíveis no momento, tente novamente em instantes"));
    }
}
