package mx.Tienda_inteligente.pls.Infraestructura;

import mx.Tienda_inteligente.pls.Enums.Enums;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * OpenAI, API de chat completions. No acepta prefill, asi que depende de que el
 * prompt del sistema pida el arreglo JSON (el parser de ClienteIa lo recorta igual).
 *
 * Sin app.ia.openai.api-key no aparece en GET /configuracion/ia/proveedores
 * y el PATCH del switch lo rechaza con 422.
 */
@Component
public class ClienteOpenAI extends ClienteLLMHttp {

    private static final String MODELO_POR_DEFECTO = "gpt-4o-mini";

    public ClienteOpenAI(@Value("${app.ia.openai.api-key:}") String apiKey,
                         @Value("${app.ia.openai.base-url:https://api.openai.com}") String baseUrl,
                         @Value("${app.ia.openai.modelo:}") String modelo,
                         @Value("${app.ia.timeout-segundos:30}") int timeoutSeg) {
        super(baseUrl, apiKey,
                modelo == null || modelo.isBlank() ? MODELO_POR_DEFECTO : modelo,
                timeoutSeg);
    }

    @Override
    public Enums.ProveedorIA proveedor() {
        return Enums.ProveedorIA.OPENAI;
    }

    @Override
    protected String ruta() {
        return "/v1/chat/completions";
    }

    @Override
    protected void cabeceras(HttpHeaders h) {
        h.setBearerAuth(apiKey);
    }

    @Override
    protected Map<String, Object> cuerpo(String sistema, String prompt, int maxTokens, String prefill) {
        return Map.of("model", modelo,
                "max_tokens", maxTokens,
                "temperature", TEMPERATURA,
                "messages", List.of(
                        Map.of("role", "system", "content", sistema),
                        Map.of("role", "user", "content", prompt)));
    }

    @Override
    protected String extraerTexto(Map<String, Object> respuesta) {
        List<Object> opciones = lista(respuesta.get("choices"));
        if (opciones.isEmpty()) {
            return "";
        }
        return texto(mapa(mapa(opciones.get(0)).get("message")).get("content"));
    }
}
