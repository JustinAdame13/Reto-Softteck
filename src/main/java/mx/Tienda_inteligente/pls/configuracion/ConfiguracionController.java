package mx.Tienda_inteligente.pls.configuracion;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/configuracion")
public class ConfiguracionController {

    private final ConfiguracionService service;

    public ConfiguracionController(ConfiguracionService service) {
        this.service = service;
    }

    @GetMapping
    public ConfiguracionDto obtener() {
        return service.obtener();
    }

    @PutMapping
    public ConfiguracionDto actualizar(@Valid @RequestBody ConfiguracionDto dto) {
        return service.actualizar(dto);
    }

    /** Switch de la demo: { "iaHabilitada": true } */
    @PatchMapping("/ia")
    public ConfiguracionDto cambiarIa(@Valid @RequestBody IaSwitchRequest request) {
        return service.cambiarIa(request.iaHabilitada());
    }
}
