package mx.Tienda_inteligente.pls.Infraestructura;

import mx.Tienda_inteligente.pls.Enums.Enums;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Anthropic (Claude), API de mensajes. Es el unico proveedor que acepta prefill,
 * que es la forma mas confiable de obligar a que la respuesta sea un arreglo JSON
 * y no venga envuelta en texto.
 */
@Component
public class ClienteAnthropic extends ClienteLLMHttp {

    private static final String MODELO_POR_DEFECTO = "claude-sonnet-4-5";

    public ClienteAnthropic(@Value("${app.ia.anthropic.api-key:}") String apiKey,
                            @Value("${app.ia.anthropic.base-url:https://api.anthropic.com}") String baseUrl,
                            @Value("${app.ia.anthropic.modelo:}") String modelo,
                            @Value("${app.ia.timeout-segundos:30}") int timeoutSeg) {
        super(baseUrl, apiKey,
                modelo == null || modelo.isBlank() ? MODELO_POR_DEFECTO : modelo,
                timeoutSeg);
    }

    @Override
    public Enums.ProveedorIA proveedor() {
        return Enums.ProveedorIA.ANTHROPIC;
    }

    @Override
    protected boolean soportaPrefill() {
        return true;
    }

    @Override
    protected String ruta() {
        return "/v1/messages";
    }

    @Override
    protected void cabeceras(HttpHeaders h) {
        h.set("x-api-key", apiKey);
        h.set("anthropic-version", "2023-06-01");
    }

    @Override
    protected Map<String, Object> cuerpo(String sistema, String prompt, int maxTokens, String prefill) {
        List<Map<String, Object>> mensajes = prefill.isBlank()
                ? List.<Map<String, Object>>of(
                        Map.of("role", "user", "content", prompt))
                : List.<Map<String, Object>>of(
                        Map.of("role", "user", "content", prompt),
                        Map.of("role", "assistant", "content", prefill));

        return Map.of("model", modelo,
                "max_tokens", maxTokens,
                "temperature", TEMPERATURA,
                "system", sistema,
                "messages", mensajes);
    }

    @Override
    protected String extraerTexto(Map<String, Object> respuesta) {
        StringBuilder sb = new StringBuilder();
        for (Object bloque : lista(respuesta.get("content"))) {
            Map<String, Object> m = mapa(bloque);
            if ("text".equals(m.get("type"))) {
                sb.append(texto(m.get("text")));
            }
        }
        return sb.toString();
    }
}
