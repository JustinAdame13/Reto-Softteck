package mx.Tienda_inteligente.pls.prediccion;

import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/predicciones")
public class PrediccionController {

    private final PrediccionService service;

    public PrediccionController(PrediccionService service) {
        this.service = service;
    }

    /** { "dias": 7, "productoIds": [10, 22], "modelo": "AUTO", "fechaInicio": "2026-10-01" } */
    @PostMapping("/generar")
    @ResponseStatus(HttpStatus.CREATED)
    public GeneracionResponse generar(@Valid @RequestBody(required = false) GenerarPrediccionRequest request) {
        return service.generar(request != null ? request : new GenerarPrediccionRequest(null, null, null, null));
    }

    @GetMapping
    public List<PrediccionResponse> listar(
            @RequestParam(required = false) Long productoId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) ModeloPrediccion modelo) {
        return service.listar(productoId, desde, hasta, modelo);
    }

    @GetMapping("/precision")
    public PrecisionResponse precision(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) ModeloPrediccion modelo) {
        return service.precision(desde, hasta, modelo);
    }

    @GetMapping("/comparar")
    public List<ComparacionItem> comparar(
            @RequestParam Long productoId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return service.comparar(productoId, desde, hasta);
    }
}
