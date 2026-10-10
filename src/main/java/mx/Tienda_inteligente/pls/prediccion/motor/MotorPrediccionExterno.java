package mx.Tienda_inteligente.pls.prediccion.motor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Motor externo (por ejemplo, el companero de algoritmos en Python con FastAPI).
 * Se activa con app.motor.tipo=externo. Recibe EntradaPrediccion en POST {app.motor.url}/predecir
 * y debe regresar una lista de ResultadoPrediccion. Si falla, el servicio usa el motor interno.
 */
@Component
public class MotorPrediccionExterno {

    private static final Logger log = LoggerFactory.getLogger(MotorPrediccionExterno.class);

    private final boolean habilitado;
    private final RestClient restClient;

    public MotorPrediccionExterno(@Value("${app.motor.tipo:interno}") String tipo,
                                  @Value("${app.motor.url:http://localhost:8000}") String url) {
        this.habilitado = "externo".equalsIgnoreCase(tipo);
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        factory.setReadTimeout(Duration.ofSeconds(60));
        this.restClient = RestClient.builder().baseUrl(url).requestFactory(factory).build();
    }

    public boolean habilitado() {
        return habilitado;
    }

    public Optional<List<ResultadoPrediccion>> predecir(EntradaPrediccion entrada) {
        if (!habilitado) return Optional.empty();
        try {
            List<ResultadoPrediccion> r = restClient.post()
                    .uri("/predecir")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(entrada)
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<ResultadoPrediccion>>() {
                    });
            return Optional.ofNullable(r);
        } catch (Exception e) {
            log.warn("El motor externo fallo, se usa el interno: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
