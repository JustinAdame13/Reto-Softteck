package mx.Tienda_inteligente.pls.producto;

import jakarta.validation.Valid;
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

@RestController
@RequestMapping("/api/v1/productos")
public class ProductoController {

    private final ProductoService service;

    public ProductoController(ProductoService service) {
        this.service = service;
    }

    @GetMapping
    public List<ProductoResponse> listar(@RequestParam(required = false) String categoria,
                                         @RequestParam(required = false) String q,
                                         @RequestParam(required = false) Boolean perecedero,
                                         @RequestParam(required = false) Boolean bajoStock,
                                         @RequestParam(required = false) Boolean activo) {
        return service.listar(categoria, q, perecedero, bajoStock, activo);
    }

    @GetMapping("/categorias")
    public List<String> categorias() {
        return service.categorias();
    }

    @GetMapping("/sku/{sku}")
    public ProductoResponse porSku(@PathVariable String sku) {
        return service.obtenerPorSku(sku);
    }

    @GetMapping("/{id}")
    public ProductoResponse obtener(@PathVariable Long id) {
        return service.obtener(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductoResponse crear(@Valid @RequestBody ProductoRequest request) {
        return service.crear(request);
    }

    @PostMapping("/lote")
    @ResponseStatus(HttpStatus.CREATED)
    public List<ProductoResponse> crearLote(@Valid @RequestBody List<@Valid ProductoRequest> lote) {
        return service.crearLote(lote);
    }

    @PutMapping("/{id}")
    public ProductoResponse actualizar(@PathVariable Long id, @Valid @RequestBody ProductoRequest request) {
        return service.actualizar(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void desactivar(@PathVariable Long id) {
        service.desactivar(id);
    }
}
