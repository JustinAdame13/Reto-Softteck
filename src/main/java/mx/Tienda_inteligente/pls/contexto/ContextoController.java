package mx.Tienda_inteligente.pls.contexto;

import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/contexto")
public class ContextoController {

    private final ContextoService service;

    public ContextoController(ContextoService service) {
        this.service = service;
    }

    @GetMapping
    public List<ContextoDto> listar(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return service.listar(desde, hasta);
    }

    @GetMapping("/{fecha}")
    public ContextoDto obtener(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        return service.obtener(fecha);
    }

    @PutMapping("/{fecha}")
    public ContextoDto guardar(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
                               @Valid @RequestBody ContextoDto dto) {
        return service.guardar(fecha, dto);
    }

    @PostMapping("/lote")
    @ResponseStatus(HttpStatus.CREATED)
    public List<ContextoDto> lote(@Valid @RequestBody List<@Valid ContextoDto> lote) {
        return service.guardarLote(lote);
    }

    @PostMapping("/generar")
    @ResponseStatus(HttpStatus.CREATED)
    public List<ContextoDto> generar(@RequestParam(defaultValue = "0") int anio) {
        return service.generar(anio == 0 ? LocalDate.now().getYear() : anio);
    }
}
