package mx.Tienda_inteligente.pls.basedatos;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** Congela la base elegida al inicio de cada peticion y la suelta al final. */
@Component
public class FiltroBase extends OncePerRequestFilter {

    private final SelectorBase selector;

    public FiltroBase(SelectorBase selector) {
        this.selector = selector;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        selector.fijar(selector.elegida());
        try {
            chain.doFilter(request, response);
        } finally {
            // Tomcat reutiliza hilos: sin esto la siguiente peticion "heredaria" la base
            selector.limpiar();
        }
    }
}
