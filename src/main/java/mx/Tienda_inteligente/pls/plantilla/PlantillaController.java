package mx.Tienda_inteligente.pls.plantilla;

import jakarta.validation.Valid;
import mx.Tienda_inteligente.pls.empleado.Rol;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/plantillas")
public class PlantillaController {

    private final PlantillaService service;

    public PlantillaController(PlantillaService service) {
        this.service = service;
    }

    @GetMapping
    public List<PlantillaResponse> listar(@RequestParam(required = false) Integer diaSemana,
                                          @RequestParam(required = false) Rol rol,
                                          @RequestParam(required = false) OrigenPlantilla origen) {
        return service.listar(diaSemana, rol, origen);
    }

    @GetMapping("/semana")
    public List<Map<String, Object>> semana() {
        return service.semana();
    }

    @GetMapping("/cobertura")
    public List<CoberturaRol> cobertura() {
        return service.cobertura();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PlantillaResponse crear(@Valid @RequestBody PlantillaRequest request) {
        return service.crear(request);
    }

    @PutMapping("/{id}")
    public PlantillaResponse actualizar(@PathVariable Long id, @Valid @RequestBody PlantillaRequest request) {
        return service.actualizar(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void borrar(@PathVariable Long id) {
        service.borrar(id);
    }

    /** guardar=false: vista previa. guardar=true: reemplaza los bloques RECOMENDADO (los MANUAL se respetan). */
    @PostMapping("/recomendar")
    public RecomendacionPersonal recomendar(@RequestParam(defaultValue = "4") int semanas,
                                            @RequestParam(defaultValue = "false") boolean guardar) {
        return service.recomendar(semanas, guardar);
    }
}
