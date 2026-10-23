package br.com.lettergames.catalogo.web;

import br.com.lettergames.contratos.Correlacao;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Garante um correlationId por requisição: reaproveita o do cliente ou gera um curto. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String id = req.getHeader(Correlacao.HEADER_HTTP);
        if (id == null || id.isBlank()) {
            id = UUID.randomUUID().toString().substring(0, 8);
        }
        MDC.put(Correlacao.MDC_KEY, id);
        res.setHeader(Correlacao.HEADER_HTTP, id);
        try {
            chain.doFilter(req, res);
        } finally {
            MDC.remove(Correlacao.MDC_KEY);
        }
    }
}
