package mx.Tienda_inteligente.pls.prediccion.motor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Datos de un producto que recibe el motor. ventasDiarias[i] son las unidades vendidas
 * el dia historialDesde + i (con ceros en los dias sin venta), hasta el dia anterior a fechaInicio.
 *
 * diasConPromocion[i] = true si ese dia el producto se vendio con promocion o descuento.
 * Esos dias NO representan la demanda normal (la inflan), asi que el motor los excluye al
 * aprender los factores, el nivel y la tendencia. Puede ser null (se asume que no hubo promos).
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
        double[] ventasDiarias,
        boolean[] diasConPromocion
) {

    /** true si el dia i tuvo promocion. Tolera null y arreglos mas cortos. */
    public boolean conPromocion(int i) {
        return diasConPromocion != null && i >= 0 && i < diasConPromocion.length && diasConPromocion[i];
    }
}
