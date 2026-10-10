package mx.Tienda_inteligente.pls.ia;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Alias de compatibilidad: el frontend ya llamaba a GET /api/v1/ia/estado.
 * La ruta oficial ahora es GET /api/v1/configuracion/ia/estado; ambas regresan lo mismo.
 */
@RestController
@RequestMapping("/api/v1/ia")
public class IaController {

    private final ClienteIa clienteIa;

    public IaController(ClienteIa clienteIa) {
        this.clienteIa = clienteIa;
    }

    @GetMapping("/estado")
    public EstadoIa estado() {
        return clienteIa.estado();
    }
}
