package mx.Tienda_inteligente.pls.Infraestructura;

import mx.Tienda_inteligente.pls.Enums.Enums;
import mx.Tienda_inteligente.pls.Interfaces.ClienteLLM;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Indexa los clientes de LLM por proveedor. Agregar un proveedor nuevo es crear
 * una clase que extienda ClienteLLMHttp: aqui no se toca nada.
 *
 * Se inyecta con ObjectProvider y no con List a proposito: si algun dia no hay
 * ningun cliente registrado, la aplicacion arranca igual y simplemente se queda
 * sin IA, en lugar de fallar con NoSuchBeanDefinitionException.
 */
@Component
public class RegistroLLM {

    private final Map<Enums.ProveedorIA, ClienteLLM> porProveedor;

    public RegistroLLM(ObjectProvider<ClienteLLM> clientes) {
        Map<Enums.ProveedorIA, ClienteLLM> indice = new EnumMap<>(Enums.ProveedorIA.class);
        clientes.orderedStream().forEach(c -> {
            if (c.proveedor() != null && c.proveedor() != Enums.ProveedorIA.NINGUNO) {
                indice.putIfAbsent(c.proveedor(), c);
            }
        });
        this.porProveedor = Map.copyOf(indice);
    }

    /** Devuelve el cliente solo si existe Y tiene credenciales para operar. */
    public Optional<ClienteLLM> obtener(Enums.ProveedorIA p) {
        if (p == null || p == Enums.ProveedorIA.NINGUNO) {
            return Optional.empty();
        }
        return Optional.ofNullable(porProveedor.get(p)).filter(ClienteLLM::disponible);
    }

    /** Los que tienen llave y modelo configurados, para el dropdown del frontend. */
    public List<Enums.ProveedorIA> disponibles() {
        return porProveedor.values().stream()
                .filter(ClienteLLM::disponible)
                .map(ClienteLLM::proveedor)
                .sorted()
                .toList();
    }

    /** Los que estan compilados en la aplicacion, con llave o sin ella. */
    public List<Enums.ProveedorIA> registrados() {
        return porProveedor.keySet().stream().sorted().toList();
    }
}
