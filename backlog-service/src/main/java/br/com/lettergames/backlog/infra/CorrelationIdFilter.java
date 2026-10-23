package br.com.lettergames.backlog.infra;

import br.com.lettergames.contratos.Correlacao;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/** Lê ou gera o {@code X-Correlation-Id}, coloca no MDC e devolve na resposta. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    // Header vem de fora: só aceitamos um formato seguro para não injetar lixo nos logs.
    private static final Pattern ID_VALIDO = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String recebido = request.getHeader(Correlacao.HEADER_HTTP);
        String correlationId = recebido != null && ID_VALIDO.matcher(recebido).matches() ? recebido : novoId();
        MDC.put(Correlacao.MDC_KEY, correlationId);
        response.setHeader(Correlacao.HEADER_HTTP, correlationId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(Correlacao.MDC_KEY);
        }
    }

    static String novoId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
