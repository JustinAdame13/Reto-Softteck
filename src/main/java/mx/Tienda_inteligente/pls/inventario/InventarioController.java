package mx.Tienda_inteligente.pls.inventario;

import jakarta.validation.Valid;
import mx.Tienda_inteligente.pls.common.Pagina;
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
import java.util.Map;

@RestController
@RequestMapping("/api/v1/inventario")
public class InventarioController {

    private final InventarioService service;

    public InventarioController(InventarioService service) {
        this.service = service;
    }

    @GetMapping("/movimientos")
    public Pagina<MovimientoResponse> listar(
            @RequestParam(required = false) Long productoId,
            @RequestParam(required = false) TipoMovimiento tipo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return service.listar(productoId, tipo, desde, hasta, page, size);
    }

    @PostMapping("/movimientos")
    @ResponseStatus(HttpStatus.CREATED)
    public MovimientoResponse registrar(@Valid @RequestBody MovimientoRequest request) {
        return service.registrar(request);
    }

    /** Carga historica. Con afectarStock=false (defecto) no cambia el stock. */
    @PostMapping("/movimientos/lote")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> lote(@Valid @RequestBody List<@Valid MovimientoRequest> lote,
                                    @RequestParam(defaultValue = "false") boolean afectarStock) {
        return service.registrarLote(lote, afectarStock);
    }

    @GetMapping("/estado")
    public List<EstadoProducto> estado(@RequestParam(required = false) Semaforo semaforo,
                                       @RequestParam(required = false) String categoria) {
        return service.estado(semaforo, categoria);
    }

    @GetMapping("/alertas")
    public List<AlertaInventario> alertas() {
        return service.alertas();
    }

    @GetMapping("/reabasto")
    public List<ReabastoItem> reabasto() {
        return service.reabasto();
    }

    @GetMapping("/perdidas")
    public Map<String, Object> perdidas(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return service.perdidas(desde, hasta);
    }
}
