package mx.Tienda_inteligente.pls.promocion;

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

@RestController
@RequestMapping("/api/v1/promociones")
public class PromocionController {

    private final PromocionService service;

    public PromocionController(PromocionService service) {
        this.service = service;
    }

    @GetMapping
    public List<PromocionResponse> listar(@RequestParam(required = false) EstadoPromocion estado,
                                          @RequestParam(required = false) OrigenPromocion origen,
                                          @RequestParam(required = false) MotivoPromocion motivo,
                                          @RequestParam(required = false) Long productoId,
                                          @RequestParam(required = false) Boolean vigentes) {
        return service.listar(estado, origen, motivo, productoId, vigentes);
    }

    @GetMapping("/{id}")
    public PromocionResponse obtener(@PathVariable Long id) {
        return service.obtener(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PromocionResponse crear(@Valid @RequestBody PromocionRequest request) {
        return service.crear(request);
    }

    @PutMapping("/{id}")
    public PromocionResponse actualizar(@PathVariable Long id, @Valid @RequestBody PromocionRequest request) {
        return service.actualizar(id, request);
    }

    /** { "estado": "ACTIVA" | "RECHAZADA" | "FINALIZADA" } */
    @PatchMapping("/{id}/estado")
    public PromocionResponse cambiarEstado(@PathVariable Long id, @Valid @RequestBody EstadoRequest request) {
        return service.cambiarEstado(id, request.estado());
    }

    /** El motor genera sugerencias. Cuerpo opcional: { "maxSugerencias": 10, "modelo": "AUTO" } */
    @PostMapping("/generar")
    @ResponseStatus(HttpStatus.CREATED)
    public List<PromocionResponse> generar(@Valid @RequestBody(required = false) GenerarPromocionesRequest request) {
        return service.generar(request != null ? request : new GenerarPromocionesRequest(null, null, null, null));
    }

    @GetMapping("/{id}/resultado")
    public ResultadoPromocion resultado(@PathVariable Long id) {
        return service.resultado(id);
    }
}
