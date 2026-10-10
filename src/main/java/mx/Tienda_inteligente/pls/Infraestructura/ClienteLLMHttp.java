package mx.Tienda_inteligente.pls.Infraestructura;

import mx.Tienda_inteligente.pls.Enums.Enums;
import mx.Tienda_inteligente.pls.Interfaces.ClienteLLM;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

public abstract class ClienteLLMHttp implements ClienteLLM {
    protected final ObjectMapper json = new ObjectMapper();
    protected final RestClient http;
    protected final String apiKey;
    protected final String modelo;

    protected ClienteLLMHttp(String baseUrl, String apiKey,
                             String modelo, int timeoutSeg) {
        this.apiKey = apiKey == null ? "" : apiKey;
        this.modelo = modelo;

        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeoutSeg * 1000);
        factory.setReadTimeout(timeoutSeg * 1000);

        this.http = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
    }

    /** Qué proveedor es, para que el registro lo indexe. */
    public abstract Enums.ProveedorIA proveedor();

    /** Si tiene credenciales para operar. */
    public boolean disponible() {
        return !apiKey.isBlank() && modelo != null && !modelo.isBlank();
    }

    // --- Los cuatro puntos donde difieren los proveedores ---
    protected abstract String ruta();
    protected abstract void cabeceras(HttpHeaders h);
    protected abstract Map<String, Object> cuerpo(String prompt);
    protected abstract String extraerTexto(JsonNode raiz);

    @Override
    public final String completar(String prompt) throws Exception {
        if (!disponible()) {
            throw new IllegalStateException(
                    "Proveedor " + proveedor() + " sin api-key o sin modelo");
        }

        String crudo = http.post()
                .uri(ruta())
                .headers(this::cabeceras)
                .body(cuerpo(prompt))
                .retrieve()
                .body(String.class);

        return extraerTexto(json.readTree(crudo));
    }

}
