package mx.Tienda_inteligente.pls.Infraestructura;

import mx.Tienda_inteligente.pls.Enums.Enums;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Google Gemini, API generateContent (v1beta). No acepta prefill, pero si acepta
 * responseMimeType=application/json, que cumple la misma funcion: garantiza
 * que la respuesta sea JSON valido y no texto con el JSON adentro.
 *
 * Familia Gemini 3 (gemini-3.x): son modelos que "piensan" antes de responder.
 * Dos ajustes respecto a los modelos 2.x, segun la documentacion de Google:
 *  - Temperatura: Google recomienda dejarla en su default (1.0); bajarla puede
 *    causar ciclos o respuestas degradadas. Por eso NO se manda en Gemini 3.
 *  - Razonamiento: se pide thinkingLevel=low (Ayax ya hizo el trabajo pesado,
 *    la IA solo ajusta) y se da margen extra de tokens, porque el razonamiento
 *    tambien consume tokens de salida y sin margen el JSON se cortaria.
 *
 * Sin app.ia.gemini.api-key no aparece en GET /configuracion/ia/proveedores.
 * Llave: Google AI Studio. Modelo: IA_GEMINI_MODELO (por defecto gemini-3.6-flash).
 */
@Component
public class ClienteGemini extends ClienteLLMHttp {

    /** gemini-2.0-flash ya fue apagado por Google; su reemplazo recomendado es gemini-3.6-flash. */
    private static final String MODELO_POR_DEFECTO = "gemini-3.6-flash";

    /** Tokens extra para el razonamiento interno de los modelos Gemini 3. */
    private static final int MARGEN_RAZONAMIENTO = 8192;

    public ClienteGemini(@Value("${app.ia.gemini.api-key:}") String apiKey,
                         @Value("${app.ia.gemini.base-url:https://generativelanguage.googleapis.com}") String baseUrl,
                         @Value("${app.ia.gemini.modelo:}") String modelo,
                         @Value("${app.ia.timeout-segundos:90}") int timeoutSeg) {
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

    /** true para la familia Gemini 3 en adelante (gemini-3, gemini-3.6-flash, ...). */
    static boolean esGemini3(String modelo) {
        String m = modelo == null ? "" : modelo.toLowerCase(java.util.Locale.ROOT);
        return m.startsWith("gemini-") && !m.startsWith("gemini-1") && !m.startsWith("gemini-2");
    }

    @Override
    protected Map<String, Object> cuerpo(String modelo, String sistema, String prompt, int maxTokens,
                                         String prefill) {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("responseMimeType", "application/json");
        if (esGemini3(modelo)) {
            config.put("maxOutputTokens", maxTokens + MARGEN_RAZONAMIENTO);
            config.put("thinkingConfig", Map.of("thinkingLevel", "low"));
        } else {
            config.put("maxOutputTokens", maxTokens);
            config.put("temperature", TEMPERATURA);
        }
        return Map.of(
                "systemInstruction", Map.of("parts", List.of(Map.of("text", sistema))),
                "contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", prompt)))),
                "generationConfig", config);
    }

    /**
     * Junta el texto del primer candidato. Si no hay texto, lanza una excepcion con el
     * motivo (prompt bloqueado, MAX_TOKENS, SAFETY...) para que ClienteIa lo registre
     * en ultimoError y se vea en GET /configuracion/ia/estado.
     */
    @Override
    protected String extraerTexto(Map<String, Object> respuesta) {
        List<Object> candidatos = lista(respuesta.get("candidates"));
        if (candidatos.isEmpty()) {
            Object bloqueo = mapa(respuesta.get("promptFeedback")).get("blockReason");
            throw new IllegalStateException("Gemini no regreso candidatos"
                    + (bloqueo != null ? " (prompt bloqueado: " + bloqueo + ")" : ""));
        }
        Map<String, Object> candidato = mapa(candidatos.get(0));
        StringBuilder sb = new StringBuilder();
        for (Object parte : lista(mapa(candidato.get("content")).get("parts"))) {
            Map<String, Object> p = mapa(parte);
            if (Boolean.TRUE.equals(p.get("thought"))) {
                continue; // razonamiento interno, no es la respuesta
            }
            sb.append(texto(p.get("text")));
        }
        if (sb.isEmpty()) {
            throw new IllegalStateException("Gemini no regreso texto (finishReason="
                    + candidato.get("finishReason") + ")");
        }
        return sb.toString();
    }
}
