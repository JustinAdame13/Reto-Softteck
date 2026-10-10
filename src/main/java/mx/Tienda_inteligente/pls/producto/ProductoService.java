package mx.Tienda_inteligente.pls.producto;

import mx.Tienda_inteligente.pls.common.ConflictoException;
import mx.Tienda_inteligente.pls.common.RecursoNoEncontradoException;
import mx.Tienda_inteligente.pls.common.ReglaNegocioException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class ProductoService {

    private final ProductoRepository repository;

    public ProductoService(ProductoRepository repository) {
        this.repository = repository;
    }

    /**
     * Lista con filtros. Se filtra en memoria porque el catalogo de una sucursal
     * es de cientos/miles de productos, no millones.
     */
    @Transactional(readOnly = true)
    public List<ProductoResponse> listar(String categoria, String q, Boolean perecedero, Boolean bajoStock, Boolean activo) {
        String texto = q == null ? null : q.toLowerCase(Locale.ROOT).trim();
        boolean soloActivos = activo == null || activo;
        return repository.findAll().stream()
                .filter(p -> p.isActivo() == soloActivos)
                .filter(p -> categoria == null || categoria.isBlank() || p.getCategoria().equalsIgnoreCase(categoria))
                .filter(p -> texto == null || texto.isEmpty()
                        || p.getNombre().toLowerCase(Locale.ROOT).contains(texto)
                        || p.getSku().toLowerCase(Locale.ROOT).contains(texto))
                .filter(p -> perecedero == null || p.isEsPerecedero() == perecedero)
                .filter(p -> bajoStock == null || (p.getStockActual() <= p.getStockMinimo()) == bajoStock)
                .sorted((a, b) -> a.getNombre().compareToIgnoreCase(b.getNombre()))
                .map(ProductoResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public Producto obtenerEntidad(Long id) {
        return repository.findById(id).orElseThrow(() -> RecursoNoEncontradoException.de("Producto", id));
    }

    @Transactional(readOnly = true)
    public ProductoResponse obtener(Long id) {
        return ProductoResponse.from(obtenerEntidad(id));
    }

    @Transactional(readOnly = true)
    public ProductoResponse obtenerPorSku(String sku) {
        return repository.findBySku(sku).map(ProductoResponse::from)
                .orElseThrow(() -> new RecursoNoEncontradoException("Producto con SKU " + sku + " no existe"));
    }

    @Transactional(readOnly = true)
    public List<String> categorias() {
        return repository.categorias();
    }

    @Transactional
    public ProductoResponse crear(ProductoRequest r) {
        if (repository.existsBySku(r.sku())) {
            throw new ConflictoException("Ya existe un producto con SKU " + r.sku());
        }
        Producto p = new Producto();
        p.setStockActual(r.stockActual() == null ? 0 : r.stockActual());
        aplicar(p, r);
        return ProductoResponse.from(repository.save(p));
    }

    /** Carga masiva: todo o nada (si un producto falla, no se guarda ninguno). */
    @Transactional
    public List<ProductoResponse> crearLote(List<ProductoRequest> lote) {
        Set<String> vistos = new HashSet<>();
        for (ProductoRequest r : lote) {
            if (!vistos.add(r.sku())) {
                throw new ConflictoException("El SKU " + r.sku() + " viene repetido en el lote");
            }
        }
        List<Producto> existentes = repository.findBySkuIn(vistos);
        if (!existentes.isEmpty()) {
            throw new ConflictoException("Ya existen productos con SKU: "
                    + existentes.stream().map(Producto::getSku).limit(10).toList());
        }
        List<Producto> nuevos = lote.stream().map(r -> {
            Producto p = new Producto();
            p.setStockActual(r.stockActual() == null ? 0 : r.stockActual());
            aplicar(p, r);
            return p;
        }).toList();
        return repository.saveAll(nuevos).stream().map(ProductoResponse::from).toList();
    }

    /** El PUT nunca toca stockActual: el stock solo cambia por ventas y movimientos. */
    @Transactional
    public ProductoResponse actualizar(Long id, ProductoRequest r) {
        Producto p = obtenerEntidad(id);
        if (!p.getSku().equals(r.sku()) && repository.existsBySku(r.sku())) {
            throw new ConflictoException("Ya existe un producto con SKU " + r.sku());
        }
        aplicar(p, r);
        return ProductoResponse.from(repository.save(p));
    }

    @Transactional
    public void desactivar(Long id) {
        Producto p = obtenerEntidad(id);
        p.setActivo(false);
        repository.save(p);
    }

    private void aplicar(Producto p, ProductoRequest r) {
        boolean perecedero = Boolean.TRUE.equals(r.esPerecedero());
        if (r.precioVenta().compareTo(r.costo()) < 0) {
            throw new ReglaNegocioException("El precio de venta no puede ser menor que el costo",
                    Map.of("precioVenta", "Debe ser mayor o igual a " + r.costo()));
        }
        if (perecedero && r.vidaUtilDias() == null) {
            throw new ReglaNegocioException("Un producto perecedero necesita vidaUtilDias",
                    Map.of("vidaUtilDias", "Obligatorio si esPerecedero = true"));
        }
        int min = r.stockMinimo() == null ? 0 : r.stockMinimo();
        if (r.stockMaximo() != null && r.stockMaximo() < min) {
            throw new ReglaNegocioException("stockMaximo no puede ser menor que stockMinimo",
                    Map.of("stockMaximo", "Debe ser >= " + min));
        }
        p.setSku(r.sku().trim());
        p.setNombre(r.nombre().trim());
        p.setCategoria(r.categoria().trim());
        p.setPrecioVenta(r.precioVenta());
        p.setCosto(r.costo());
        p.setStockMinimo(min);
        p.setStockMaximo(r.stockMaximo());
        p.setLeadTimeDias(r.leadTimeDias() == null ? 1 : r.leadTimeDias());
        p.setEsPerecedero(perecedero);
        p.setVidaUtilDias(perecedero ? r.vidaUtilDias() : null);
    }
}
