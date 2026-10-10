package mx.Tienda_inteligente.pls.Infraestructura;

import mx.Tienda_inteligente.pls.Interfaces.ClienteLLM;
import org.springframework.boot.json.JsonParser;
import org.springframework.boot.json.JsonParserFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
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

    protected abstract String ruta();

    protected abstract void cabeceras(HttpHeaders h);

    protected abstract Map<String, Object> cuerpo(String sistema, String prompt, int maxTokens, String prefill);

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
    public final String completar(String sistema, String prompt, int maxTokens, String prefill) throws Exception {
        if (!disponible()) {
            throw new IllegalStateException("Proveedor " + proveedor() + " sin api-key o sin modelo");
        }
        String pre = prefill == null ? "" : prefill;

        String crudo = http.post()
                .uri(ruta())
                .headers(this::cabeceras)
                .contentType(MediaType.APPLICATION_JSON)
                .body(cuerpo(sistema, prompt, maxTokens, pre))
                .retrieve()
                .body(String.class);

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
