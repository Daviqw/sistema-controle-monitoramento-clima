package br.edu.ucsal.clima.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class FrontendOriginFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(FrontendOriginFilter.class);
    private final String origin;
    private final String sharedKey;
    public FrontendOriginFilter(@Value("${app.security.frontend.origin}") String origin,
                                @Value("${app.security.frontend.shared-key}") String sharedKey) {
        this.origin = origin; this.sharedKey = sharedKey;
    }
    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.equals("/actuator/health") || path.equals("/error");
    }
    @Override protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res,
            FilterChain chain) throws ServletException, IOException {
        String requestOrigin = req.getHeader("Origin");
        if (requestOrigin == null) requestOrigin = req.getHeader("Referer");
        boolean originOk = false;
        if(requestOrigin!=null) {
            try {
                var recebido=java.net.URI.create(requestOrigin);
                var esperado=java.net.URI.create(origin);
                originOk=java.util.Objects.equals(recebido.getScheme(),esperado.getScheme())
                    && java.util.Objects.equals(recebido.getHost(),esperado.getHost())
                    && recebido.getPort()==esperado.getPort();
            } catch(IllegalArgumentException ignored) { }
        }
        boolean keyOk = sharedKey != null && !sharedKey.isBlank()
            && sharedKey.equals(req.getHeader("X-Frontend-Key"));
        if (!originOk || !keyOk) {
            log.warn("Acesso bloqueado por filtro de origem/chave: path={}, ip={}",
                req.getRequestURI(), req.getRemoteAddr());
            res.setStatus(HttpServletResponse.SC_FORBIDDEN);
            res.setContentLength(0);
            return;
        }
        chain.doFilter(req, res);
    }
}
