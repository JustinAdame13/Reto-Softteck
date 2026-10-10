package mx.Tienda_inteligente.pls.ia;

import mx.Tienda_inteligente.pls.configuracion.ConfiguracionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/ia")
public class IaController {

    private final ClienteIa clienteIa;
    private final ConfiguracionService configuracionService;

    public IaController(ClienteIa clienteIa, ConfiguracionService configuracionService) {
        this.clienteIa = clienteIa;
        this.configuracionService = configuracionService;
    }

    /** Para que el frontend sepa si puede mostrar el switch de IA. */
    @GetMapping("/estado")
    public Map<String, Object> estado() {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("configurada", clienteIa.disponible());
        r.put("habilitada", configuracionService.obtenerEntidad().isIaHabilitada());
        r.put("activa", configuracionService.iaActiva());
        r.put("modelo", clienteIa.getModelo());
        return r;
    }
}
