package mx.Tienda_inteligente.pls.venta;

import jakarta.validation.Valid;
import mx.Tienda_inteligente.pls.common.Pagina;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Sin DELETE: una devolucion se registra como movimiento de inventario tipo DEVOLUCION. */
@RestController
@RequestMapping("/api/v1/ventas")
public class VentaController {

    private final VentaService service;

    public VentaController(VentaService service) {
        this.service = service;
    }

    @GetMapping
    public Pagina<VentaResponse> listar(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) Long productoId,
            @RequestParam(required = false) Long empleadoId,
            @RequestParam(required = false) Long promocionId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return service.listar(desde, hasta, productoId, empleadoId, promocionId, page, size);
    }

    @GetMapping("/ticket/{ticket}")
    public TicketResponse ticket(@PathVariable String ticket) {
        return service.ticket(ticket);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TicketResponse registrar(@Valid @RequestBody TicketRequest request) {
        return service.registrar(request);
    }

    /** Importa historicos. No toca el stock. */
    @PostMapping("/lote")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> lote(@Valid @RequestBody List<@Valid VentaLoteRequest> lote) {
        return service.importarLote(lote);
    }

    /** agrupar = dia | semana | mes | hora | diaSemana | producto | categoria */
    @GetMapping("/resumen")
    public List<ResumenVentasItem> resumen(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(defaultValue = "dia") String agrupar,
            @RequestParam(required = false) Long productoId,
            @RequestParam(required = false) String categoria) {
        return service.resumen(desde, hasta, agrupar, productoId, categoria);
    }

    @GetMapping("/records")
    public RecordsVentas records(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return service.records(desde, hasta);
    }
}
