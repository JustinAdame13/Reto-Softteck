package mx.Tienda_inteligente.pls.ia;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.json.JsonParser;
import org.springframework.boot.json.JsonParserFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Cliente minimo para la API de mensajes de Anthropic (Claude).
 * La IA es OPCIONAL: si no hay API key, disponible() = false y los motores usan solo el algoritmo BASE.
 * No es un chatbot: se le manda un prompt con datos y se le pide una respuesta en JSON.
 */
@Component
public class ClienteIa {

    private static final Logger log = LoggerFactory.getLogger(ClienteIa.class);
    private static final String MODELO_POR_DEFECTO = "claude-sonnet-4-5";

    private final String apiKey;
    private final String modelo;
    private final RestClient restClient;
    private final JsonParser jsonParser = JsonParserFactory.getJsonParser();

    public ClienteIa(@Value("${app.ia.api-key:}") String apiKey,
                     @Value("${app.ia.base-url:https://api.anthropic.com}") String baseUrl,
                     @Value("${app.ia.modelo:}") String modelo,
                     @Value("${app.ia.timeout-segundos:30}") int timeoutSegundos) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.modelo = (modelo == null || modelo.isBlank()) ? MODELO_POR_DEFECTO : modelo.trim();

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(timeoutSegundos));

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .defaultHeader("anthropic-version", "2023-06-01")
                .build();
    }

    public boolean disponible() {
        return !apiKey.isEmpty();
    }

    public String getModelo() {
        return modelo;
    }

    /**
     * Manda un prompt y regresa el texto de la respuesta.
     * Nunca lanza excepcion: si algo falla regresa Optional.empty() y el llamador usa el algoritmo BASE.
     */
    public Optional<String> completar(String instruccionesSistema, String prompt, int maxTokens) {
        if (!disponible()) {
            return Optional.empty();
        }
        try {
            Map<String, Object> body = Map.of(
                    "model", modelo,
                    "max_tokens", maxTokens,
                    "system", instruccionesSistema,
                    "messages", List.of(Map.of("role", "user", "content", prompt)));

            String respuesta = restClient.post()
                    .uri("/v1/messages")
                    .header("x-api-key", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);

            Map<String, Object> json = jsonParser.parseMap(respuesta);
            Object content = json.get("content");
            if (content instanceof List<?> bloques) {
                StringBuilder texto = new StringBuilder();
                for (Object b : bloques) {
                    if (b instanceof Map<?, ?> m && "text".equals(m.get("type"))) {
                        texto.append(m.get("text"));
                    }
                }
                return Optional.of(texto.toString());
            }
            return Optional.empty();
        } catch (Exception e) {
            log.warn("La llamada a la IA fallo, se usara el algoritmo BASE: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Pide a la IA una respuesta y extrae el primer arreglo JSON que venga en el texto.
     * Regresa una lista de objetos (Map) o Optional.empty() si no se pudo.
     */
    public Optional<List<Object>> completarJsonArreglo(String instruccionesSistema, String prompt, int maxTokens) {
        return completar(instruccionesSistema, prompt, maxTokens).flatMap(texto -> {
            int inicio = texto.indexOf('[');
            int fin = texto.lastIndexOf(']');
            if (inicio < 0 || fin <= inicio) {
                log.warn("La IA no regreso un arreglo JSON");
                return Optional.empty();
            }
            try {
                return Optional.of(jsonParser.parseList(texto.substring(inicio, fin + 1)));
            } catch (Exception e) {
                log.warn("No se pudo leer el JSON de la IA: {}", e.getMessage());
                return Optional.empty();
            }
        });
    }

    /** Helpers para leer numeros de los Map que regresa el parser. */
    public static Double numero(Object valor) {
        if (valor instanceof Number n) {
            return n.doubleValue();
        }
        if (valor instanceof String s) {
            try {
                return Double.parseDouble(s);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
}
