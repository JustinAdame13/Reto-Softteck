package mx.Tienda_inteligente.pls.configuracion;

import jakarta.validation.Valid;
import mx.Tienda_inteligente.pls.Enums.*;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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

    /** Para llenar el dropdown: solo los que tienen API key en el servidor. */
    @GetMapping("/ia/proveedores")
    public List<Enums.ProveedorIA> proveedoresDisponibles() {
        return service.proveedoresDisponibles();
    }

    /**
     * Switch de la demo. Apagar: { "proveedorIa": "NINGUNO" }
     * Prender:                   { "proveedorIa": "GEMINI", "modeloIa": "gemini-2.0-flash" }
     */
    @PatchMapping("/ia")
    public ConfiguracionDto cambiarIa(@Valid @RequestBody IaSwitchRequest request) {
        return service.cambiarIa(request.proveedorIa(), request.modeloIa());
    }
}