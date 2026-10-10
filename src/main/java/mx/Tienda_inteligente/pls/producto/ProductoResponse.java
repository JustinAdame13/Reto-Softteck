package mx.Tienda_inteligente.pls.producto;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record ProductoResponse(
        Long id,
        String sku,
        String nombre,
        String categoria,
        BigDecimal precioVenta,
        BigDecimal costo,
        BigDecimal margenPorcentaje,
        Integer stockActual,
        Integer stockMinimo,
        Integer stockMaximo,
        Integer leadTimeDias,
        boolean esPerecedero,
        Integer vidaUtilDias,
        boolean activo
) {

    public static ProductoResponse from(Producto p) {
        BigDecimal margen = BigDecimal.ZERO;
        if (p.getPrecioVenta() != null && p.getPrecioVenta().signum() > 0) {
            margen = p.getPrecioVenta().subtract(p.getCosto())
                    .multiply(BigDecimal.valueOf(100))
                    .divide(p.getPrecioVenta(), 2, RoundingMode.HALF_UP);
        }
        return new ProductoResponse(p.getId(), p.getSku(), p.getNombre(), p.getCategoria(),
                p.getPrecioVenta(), p.getCosto(), margen, p.getStockActual(), p.getStockMinimo(),
                p.getStockMaximo(), p.getLeadTimeDias(), p.isEsPerecedero(), p.getVidaUtilDias(), p.isActivo());
    }
}
