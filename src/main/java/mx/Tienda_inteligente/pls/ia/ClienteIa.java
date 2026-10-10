package mx.Tienda_inteligente.pls.ia;

import mx.Tienda_inteligente.pls.Enums.Enums;
import mx.Tienda_inteligente.pls.Infraestructura.RegistroLLM;
import mx.Tienda_inteligente.pls.Interfaces.ClienteLLM;
import mx.Tienda_inteligente.pls.configuracion.ConfiguracionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.json.JsonParser;
import org.springframework.boot.json.JsonParserFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Punto unico de entrada a la IA para todo el backend (motor de prediccion y
 * motor de promociones). No es un chatbot: se le manda un prompt con datos y se
 * le pide una respuesta en JSON.
 *
 * Quien decide QUE proveedor se usa es la columna proveedor_ia de la tabla
 * configuracion, que el gerente cambia en caliente con
 * PATCH /api/v1/configuracion/ia. Esta clase solo lo resuelve contra el registro.
 *
 * La IA es OPCIONAL y NUNCA tumba una peticion: si no hay proveedor, si no hay
 * llave o si la llamada falla, regresa Optional.empty() y quien llama se queda
 * con Ayax sin IA (modelo BASE).
 */
@Component
public class ClienteIa {

    private static final Logger log = LoggerFactory.getLogger(ClienteIa.class);

    private final RegistroLLM registro;
    private final ConfiguracionService configuracionService;
    private final JsonParser jsonParser = JsonParserFactory.getJsonParser();

    private final AtomicLong llamadas = new AtomicLong();
    private final AtomicLong fallos = new AtomicLong();
    private volatile Boolean ultimaOk;
    private volatile Long ultimaLatenciaMs;
    private volatile String ultimoError;

    public ClienteIa(RegistroLLM registro, ConfiguracionService configuracionService) {
        this.registro = registro;
        this.configuracionService = configuracionService;
    }

    /** El cliente del proveedor elegido en la tabla, si tiene llave. */
    public Optional<ClienteLLM> clienteActivo() {
        return registro.obtener(configuracionService.obtenerEntidad().getProveedorIa());
    }

    /** true si hay un proveedor elegido Y ese proveedor puede operar. */
    public boolean disponible() {
        return clienteActivo().isPresent();
    }

    /**
     * El modelo que se usaria ahora mismo, o cadena vacia si la IA esta apagada.
     * Si el gerente eligio uno con PATCH /configuracion/ia (columna modelo_ia) gana ese;
     * si no, el por defecto del proveedor (.env o codigo).
     */
    public String getModelo() {
        return clienteActivo().map(c -> modeloEfectivo(c, modeloElegido())).orElse("");
    }

    private String modeloElegido() {
        return configuracionService.obtenerEntidad().getModeloIa();
    }

    private static String modeloEfectivo(ClienteLLM cliente, String elegido) {
        return elegido == null || elegido.isBlank() ? cliente.modelo() : elegido.trim();
    }

    public EstadoIa estado() {
        var conf = configuracionService.obtenerEntidad();
        return new EstadoIa(
                conf.getProveedorIa(),
                getModelo(),
                disponible(),
                registro.registrados(),
                registro.disponibles(),
                llamadas.get(),
                fallos.get(),
                ultimaOk,
                ultimaLatenciaMs,
                ultimoError);
    }

    /**
     * Manda un prompt y regresa el texto de la respuesta.
     * Nunca lanza excepcion: si algo falla regresa Optional.empty().
     */
    public Optional<String> completar(String instruccionesSistema, String prompt, int maxTokens) {
        return llamar(instruccionesSistema, prompt, maxTokens, "");
    }

    /**
     * Pide una respuesta y extrae el primer arreglo JSON que venga en el texto.
     * Con Anthropic se fuerza por prefill; con los demas se recorta del primer '['
     * al ultimo ']'. Regresa una lista de objetos (Map) o Optional.empty().
     */
    public Optional<List<Object>> completarJsonArreglo(String instruccionesSistema, String prompt, int maxTokens) {
        return llamar(instruccionesSistema, prompt, maxTokens, "[").flatMap(texto -> {
            int inicio = texto.indexOf('[');
            int fin = texto.lastIndexOf(']');
            if (inicio < 0 || fin <= inicio) {
                registrarFallo("La IA no regreso un arreglo JSON");
                return Optional.empty();
            }
            try {
                return Optional.of(jsonParser.parseList(texto.substring(inicio, fin + 1)));
            } catch (Exception e) {
                registrarFallo("No se pudo leer el JSON de la IA: " + e.getMessage());
                return Optional.empty();
            }
        });
    }

    private Optional<String> llamar(String sistema, String prompt, int maxTokens, String prefill) {
        Optional<ClienteLLM> cliente = clienteActivo();
        if (cliente.isEmpty()) {
            return Optional.empty();
        }
        llamadas.incrementAndGet();
        long t0 = System.nanoTime();
        try {
            String modelo = modeloEfectivo(cliente.get(), modeloElegido());
            String texto = cliente.get().completar(modelo, sistema, prompt, maxTokens, prefill);
            ultimaLatenciaMs = (System.nanoTime() - t0) / 1_000_000;
            ultimaOk = true;
            ultimoError = null;
            return Optional.ofNullable(texto).filter(s -> !s.isBlank());
        } catch (Exception e) {
            ultimaLatenciaMs = (System.nanoTime() - t0) / 1_000_000;
            registrarFallo(e.getClass().getSimpleName() + ": " + e.getMessage());
            return Optional.empty();
        }
    }

    private void registrarFallo(String mensaje) {
        fallos.incrementAndGet();
        ultimaOk = false;
        ultimoError = mensaje;
        log.warn("La llamada a la IA fallo, se usara Ayax sin IA (BASE): {}", mensaje);
    }

    /** Helper para leer numeros de los Map que regresa el parser. */
    public static Double numero(Object valor) {
        if (valor instanceof Number n) {
            return n.doubleValue();
        }
        if (valor instanceof String s) {
            try {
                return Double.parseDouble(s.trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    /** Por claridad en los logs y en /estado. */
    public Enums.ProveedorIA proveedorActivo() {
        return clienteActivo().map(ClienteLLM::proveedor).orElse(Enums.ProveedorIA.NINGUNO);
    }
}
