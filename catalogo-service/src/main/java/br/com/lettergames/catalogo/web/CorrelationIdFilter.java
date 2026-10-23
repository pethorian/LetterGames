package br.com.lettergames.catalogo.web;

import br.com.lettergames.contratos.Correlacao;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Garante um correlationId por requisição: reaproveita o do cliente se for válido ou gera um curto. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    /** Mesma regra do backlog-service: evita injetar quebras de linha ou lixo nos logs. */
    private static final Pattern ID_VALIDO = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String id = req.getHeader(Correlacao.HEADER_HTTP);
        if (id == null || !ID_VALIDO.matcher(id).matches()) {
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
