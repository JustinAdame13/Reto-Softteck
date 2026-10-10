package mx.Tienda_inteligente.pls.basedatos;

import jakarta.validation.Valid;
import mx.Tienda_inteligente.pls.configuracion.Tamano;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Selector de base para la demo.
 *   GET -> { "activa": "MEDIANA", "disponibles": ["PEQUENA","MEDIANA","GRANDE"] }
 *   PUT { "tamano": "GRANDE" } -> cambia la base para todas las peticiones siguientes.
 * Despues de cambiar, el frontend debe volver a pedir todos sus datos.
 */
@RestController
@RequestMapping("/api/v1/sucursal-activa")
public class SucursalActivaController {

    private final SelectorBase selector;

    public SucursalActivaController(SelectorBase selector) {
        this.selector = selector;
    }

    @GetMapping
    public Map<String, Object> actual() {
        return Map.of("activa", selector.elegida(), "disponibles", Tamano.values());
    }

    @PutMapping
    public Map<String, Object> cambiar(@Valid @RequestBody CambioBaseRequest request) {
        selector.cambiar(request.tamano());
        return actual();
    }
}
