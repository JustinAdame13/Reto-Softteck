package mx.Tienda_inteligente.pls.Infraestructura;

import mx.Tienda_inteligente.pls.Enums.Enums;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Google Gemini, generateContent. No acepta prefill, pero si acepta
 * responseMimeType=application/json, que cumple la misma funcion: garantiza
 * que la respuesta sea JSON valido y no texto con el JSON adentro.
 *
 * Sin app.ia.gemini.api-key no aparece en GET /configuracion/ia/proveedores.
 */
@Component
public class ClienteGemini extends ClienteLLMHttp {

    private static final String MODELO_POR_DEFECTO = "gemini-2.0-flash";

    public ClienteGemini(@Value("${app.ia.gemini.api-key:}") String apiKey,
                         @Value("${app.ia.gemini.base-url:https://generativelanguage.googleapis.com}") String baseUrl,
                         @Value("${app.ia.gemini.modelo:}") String modelo,
                         @Value("${app.ia.timeout-segundos:30}") int timeoutSeg) {
        super(baseUrl, apiKey,
                modelo == null || modelo.isBlank() ? MODELO_POR_DEFECTO : modelo,
                timeoutSeg);
    }

    @Override
    public Enums.ProveedorIA proveedor() {
        return Enums.ProveedorIA.GEMINI;
    }

    @Override
    protected String ruta(String modelo) {
        return "/v1beta/models/" + modelo + ":generateContent";
    }

    @Override
    protected void cabeceras(HttpHeaders h) {
        h.set("x-goog-api-key", apiKey);
    }

    @Override
    protected Map<String, Object> cuerpo(String modelo, String sistema, String prompt, int maxTokens,
                                         String prefill) {
        return Map.of(
                "systemInstruction", Map.of("parts", List.of(Map.of("text", sistema))),
                "contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", prompt)))),
                "generationConfig", Map.of(
                        "temperature", TEMPERATURA,
                        "maxOutputTokens", maxTokens,
                        "responseMimeType", "application/json"));
    }

    @Override
    protected String extraerTexto(Map<String, Object> respuesta) {
        List<Object> candidatos = lista(respuesta.get("candidates"));
        if (candidatos.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Object parte : lista(mapa(mapa(candidatos.get(0)).get("content")).get("parts"))) {
            sb.append(texto(mapa(parte).get("text")));
        }
        return sb.toString();
    }
}
