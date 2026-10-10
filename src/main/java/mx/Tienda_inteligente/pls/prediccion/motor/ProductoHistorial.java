package mx.Tienda_inteligente.pls.prediccion.motor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Datos de un producto que recibe el motor. ventasDiarias[i] son las unidades vendidas
 * el dia historialDesde + i (con ceros en los dias sin venta), hasta el dia anterior a fechaInicio.
 */
public record ProductoHistorial(
        Long productoId,
        String sku,
        String nombre,
        String categoria,
        BigDecimal precioVenta,
        BigDecimal costo,
        int stockActual,
        int leadTimeDias,
        boolean esPerecedero,
        Integer vidaUtilDias,
        LocalDate historialDesde,
        double[] ventasDiarias
) {
}
