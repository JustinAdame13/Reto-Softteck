package mx.Tienda_inteligente.pls.Infraestructura;

import mx.Tienda_inteligente.pls.Enums.Enums;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class RegistroLLM {
    private final Map<Enums.ProveedorIA, ClienteLLMHttp> porProveedor;

    /**
     * Spring inyecta aqui TODOS los beans que extiendan ClienteLLMHttp.
     * Agregar un proveedor nuevo no requiere tocar esta clase.
     */
    public RegistroLLM(List<ClienteLLMHttp> clientes) {
        this.porProveedor = clientes.stream()
                .collect(Collectors.toMap(
                        ClienteLLMHttp::proveedor,
                        Function.identity()));
    }

    /** Devuelve el cliente solo si existe Y tiene API key. */
    public Optional<ClienteLLMHttp> obtener(Enums.ProveedorIA p) {
        if (p == null || p == Enums.ProveedorIA.NINGUNO) {
            return Optional.empty();
        }
        return Optional.ofNullable(porProveedor.get(p))
                .filter(ClienteLLMHttp::disponible);
    }

    /** Los que tienen llave configurada, para el dropdown del frontend. */
    public List<Enums.ProveedorIA> disponibles() {
        return porProveedor.values().stream()
                .filter(ClienteLLMHttp::disponible)
                .map(ClienteLLMHttp::proveedor)
                .sorted()
                .toList();
    }
}
