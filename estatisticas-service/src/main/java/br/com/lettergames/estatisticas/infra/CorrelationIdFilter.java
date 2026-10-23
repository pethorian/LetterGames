package br.com.lettergames.estatisticas.infra;

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

/** Lê (ou gera, se ausente ou inválido) o {@code X-Correlation-Id}, põe no MDC e devolve no header da resposta. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String correlationId = request.getHeader(Correlacao.HEADER_HTTP);
        // Fora do padrão (vazio, longo, com espaço ou quebra de linha): descarta e gera um novo.
        if (!CorrelationIds.valido(correlationId)) {
            correlationId = CorrelationIds.novo();
        }
        MDC.put(Correlacao.MDC_KEY, correlationId);
        response.setHeader(Correlacao.HEADER_HTTP, correlationId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(Correlacao.MDC_KEY);
        }
    }
}
