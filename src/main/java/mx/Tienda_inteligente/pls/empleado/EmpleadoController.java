package mx.Tienda_inteligente.pls.empleado;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Sin DELETE fisico: las ventas apuntan a los empleados. */
@RestController
@RequestMapping("/api/v1/empleados")
public class EmpleadoController {

    private final EmpleadoService service;

    public EmpleadoController(EmpleadoService service) {
        this.service = service;
    }

    @GetMapping
    public List<EmpleadoResponse> listar(@RequestParam(required = false) Rol rol,
                                         @RequestParam(required = false) Boolean activo) {
        return service.listar(rol, activo);
    }

    @GetMapping("/{id}")
    public EmpleadoResponse obtener(@PathVariable Long id) {
        return service.obtener(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EmpleadoResponse crear(@Valid @RequestBody EmpleadoRequest request) {
        return service.crear(request);
    }

    @PostMapping("/lote")
    @ResponseStatus(HttpStatus.CREATED)
    public List<EmpleadoResponse> crearLote(@Valid @RequestBody List<@Valid EmpleadoRequest> lote) {
        return service.crearLote(lote);
    }

    @PutMapping("/{id}")
    public EmpleadoResponse actualizar(@PathVariable Long id, @Valid @RequestBody EmpleadoRequest request) {
        return service.actualizar(id, request);
    }

    @PatchMapping("/{id}/activo")
    public EmpleadoResponse cambiarActivo(@PathVariable Long id, @Valid @RequestBody ActivoRequest request) {
        return service.cambiarActivo(id, request.activo());
    }
}
