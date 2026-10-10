package mx.Tienda_inteligente.pls.Infraestructura;

import mx.Tienda_inteligente.pls.Interfaces.ClienteLLM;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.json.JsonParser;
import org.springframework.boot.json.JsonParserFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Base HTTP comun a todos los proveedores. Lo que cambia de uno a otro queda en
 * cuatro metodos: la ruta, las cabeceras, el cuerpo y como sacar el texto.
 */
public abstract class ClienteLLMHttp implements ClienteLLM {

    /**
     * Determinismo. Sin temperatura 0 la misma prediccion da numeros distintos en
     * cada corrida y /predicciones/comparar se ve inestable frente al jurado.
     */
    protected static final double TEMPERATURA = 0.0;

    /**
     * Esperas entre reintentos cuando el proveedor esta saturado (429/5xx).
     * 2 reintentos: 2 s y luego 5 s. Si sigue fallando, el lote se queda con Ayax sin IA.
     */
    private static final long[] ESPERAS_REINTENTO_MS = {2_000, 5_000};

    private static final Logger log = LoggerFactory.getLogger(ClienteLLMHttp.class);

    protected final JsonParser json = JsonParserFactory.getJsonParser();
    protected final RestClient http;
    protected final String apiKey;
    protected final String modelo;

    protected ClienteLLMHttp(String baseUrl, String apiKey, String modelo, int timeoutSeg) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.modelo = modelo == null ? "" : modelo.trim();

        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
        factory.setReadTimeout(Duration.ofSeconds(timeoutSeg));

        this.http = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
    }

    // --- Los cuatro puntos donde difieren los proveedores ---

    /** @param modelo el modelo efectivo de esta llamada (Gemini lo lleva en la ruta). */
    protected abstract String ruta(String modelo);

    protected abstract void cabeceras(HttpHeaders h);

    protected abstract Map<String, Object> cuerpo(String modelo, String sistema, String prompt, int maxTokens,
                                                  String prefill);

    protected abstract String extraerTexto(Map<String, Object> respuesta);

    /** true si el proveedor acepta que la respuesta venga empezada (prefill). */
    protected boolean soportaPrefill() {
        return false;
    }

    @Override
    public final String modelo() {
        return modelo;
    }

    @Override
    public boolean disponible() {
        return !apiKey.isBlank() && !modelo.isBlank();
    }

    @Override
    public final String completar(String modeloElegido, String sistema, String prompt, int maxTokens,
                                  String prefill) throws Exception {
        if (!disponible()) {
            throw new IllegalStateException("Proveedor " + proveedor() + " sin api-key o sin modelo");
        }
        String pre = prefill == null ? "" : prefill;
        String m = modeloElegido == null || modeloElegido.isBlank() ? modelo : modeloElegido.trim();

        Map<String, Object> peticion = cuerpo(m, sistema, prompt, maxTokens, pre);
        String crudo = null;
        for (int intento = 0; ; intento++) {
            try {
                crudo = http.post()
                        .uri(ruta(m))
                        .headers(this::cabeceras)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(peticion)
                        .retrieve()
                        .body(String.class);
                break;
            } catch (HttpStatusCodeException e) {
                // 429 (limite de uso) y 5xx (servidor saturado, p. ej. Gemini "high demand")
                // suelen ser momentaneos: se reintenta con espera creciente.
                // Cualquier otro error (400 peticion mala, 401/403 llave) no mejora reintentando.
                int status = e.getStatusCode().value();
                boolean temporal = status == 429 || status >= 500;
                if (!temporal || intento >= ESPERAS_REINTENTO_MS.length) {
                    throw e;
                }
                log.warn("{} respondio {} (intento {}); se reintenta en {} ms",
                        proveedor(), status, intento + 1, ESPERAS_REINTENTO_MS[intento]);
                Thread.sleep(ESPERAS_REINTENTO_MS[intento]);
            }
        }

        String textoRespuesta = extraerTexto(json.parseMap(crudo == null || crudo.isBlank() ? "{}" : crudo));

        // Si el proveedor acepto el prefill, su respuesta NO lo incluye: se lo devolvemos.
        return soportaPrefill() && !pre.isBlank() ? pre + textoRespuesta : textoRespuesta;
    }

    // --- Helpers para navegar el JSON sin amarrarse a una version de Jackson ---

    @SuppressWarnings("unchecked")
    protected static Map<String, Object> mapa(Object o) {
        return o instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    }

    @SuppressWarnings("unchecked")
    protected static List<Object> lista(Object o) {
        return o instanceof List<?> l ? (List<Object>) l : List.of();
    }

    protected static String texto(Object o) {
        return o == null ? "" : o.toString();
    }
}
