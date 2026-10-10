package mx.Tienda_inteligente.pls.inventario;

import mx.Tienda_inteligente.pls.analitica.EstadisticasVentas;
import mx.Tienda_inteligente.pls.analitica.SerieDiaria;
import mx.Tienda_inteligente.pls.common.Dinero;
import mx.Tienda_inteligente.pls.producto.Producto;
import mx.Tienda_inteligente.pls.producto.ProductoRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

/**
 * Calcula el estado de inventario de cada producto con el modelo clasico de punto de reorden:
 * <pre>
 *   stockSeguridad = Z x desviacion diaria x raiz(leadTime)       (Z = 1.65 -> 95 % de nivel de servicio)
 *   puntoReorden   = demanda diaria x leadTime + stockSeguridad
 *   diasCobertura  = stock / demanda diaria
 * </pre>
 * Caducidad aproximada (no hay lotes): ultima ENTRADA + vidaUtilDias.
 */
@Component
public class AnalisisInventario {

    public static final double Z_NIVEL_SERVICIO = 1.65;
    public static final int VENTANA_DIAS = 28;

    private final ProductoRepository productoRepository;
    private final EstadisticasVentas estadisticas;

    public AnalisisInventario(ProductoRepository productoRepository, EstadisticasVentas estadisticas) {
        this.productoRepository = productoRepository;
        this.estadisticas = estadisticas;
    }

    @Transactional(readOnly = true)
    public List<EstadoProducto> calcular() {
        LocalDate hoy = LocalDate.now();
        LocalDate desde = hoy.minusDays(VENTANA_DIAS - 1L);
        List<Producto> productos = productoRepository.findByActivoTrueOrderByNombre();
        Map<Long, SerieDiaria> series = estadisticas.series(desde, hoy, null);
        Map<Long, LocalDate> entradas = estadisticas.ultimaEntrada();

        return productos.stream().map(p -> {
            SerieDiaria s = series.getOrDefault(p.getId(), estadisticas.serieVacia(p.getId(), desde, hoy));
            return calcular(p, s, entradas.get(p.getId()), hoy);
        }).toList();
    }

    public EstadoProducto calcular(Producto p, SerieDiaria s, LocalDate ultimaEntrada, LocalDate hoy) {
        double media = s.promedio();
        double sigma = s.desviacionUltimos(VENTANA_DIAS);
        int lead = Math.max(1, p.getLeadTimeDias());
        int stock = p.getStockActual();

        int seguridad = (int) Math.ceil(Z_NIVEL_SERVICIO * sigma * Math.sqrt(lead));
        int reorden = Math.max(p.getStockMinimo(), (int) Math.ceil(media * lead + seguridad));
        Double cobertura = media > 0 ? Dinero.r2(stock / media) : null;

        LocalDate caducidad = null;
        Integer diasCaducar = null;
        Integer enRiesgo = null;
        if (p.isEsPerecedero() && p.getVidaUtilDias() != null && ultimaEntrada != null && stock > 0) {
            caducidad = ultimaEntrada.plusDays(p.getVidaUtilDias());
            diasCaducar = (int) ChronoUnit.DAYS.between(hoy, caducidad);
            int seVenderan = (int) Math.floor(media * Math.max(0, diasCaducar));
            enRiesgo = Math.max(0, stock - seVenderan);
        }

        Semaforo semaforo;
        boolean sobrestock = (p.getStockMaximo() != null && stock > p.getStockMaximo())
                || (cobertura != null && cobertura > Math.max(60, 4.0 * (lead + 7)))
                || (p.isEsPerecedero() && p.getVidaUtilDias() != null && cobertura != null && cobertura > p.getVidaUtilDias());
        if (stock <= 0) {
            semaforo = Semaforo.AGOTADO;
        } else if (stock <= seguridad) {
            semaforo = Semaforo.CRITICO;
        } else if (stock <= reorden) {
            semaforo = Semaforo.BAJO;
        } else if (sobrestock) {
            semaforo = Semaforo.SOBRESTOCK;
        } else {
            semaforo = Semaforo.OK;
        }

        return new EstadoProducto(p.getId(), p.getSku(), p.getNombre(), p.getCategoria(), stock,
                p.getStockMinimo(), p.getStockMaximo(), lead, Dinero.r2(media), Dinero.r2(sigma),
                seguridad, reorden, cobertura, s.diasSinVenta(), semaforo,
                Dinero.redondear(p.getCosto().multiply(BigDecimal.valueOf(Math.max(0, stock)))),
                p.isEsPerecedero(), caducidad, diasCaducar, enRiesgo, p.getCosto(), p.getPrecioVenta());
    }
}
