package mx.Tienda_inteligente.pls.promocion;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Reglas de precio de una promocion. Las usan la caja (ventas), el CRUD de promociones
 * y el motor que genera sugerencias, para que todos calculen exactamente igual.
 */
public final class CalculadoraPromocion {

    private static final BigDecimal CIEN = BigDecimal.valueOf(100);

    private CalculadoraPromocion() {
    }

    /** Precio efectivo por unidad con la promocion aplicada. */
    public static BigDecimal precioEfectivoUnitario(TipoPromocion tipo, BigDecimal valor, BigDecimal precio) {
        return switch (tipo) {
            case PORCENTAJE -> precio.multiply(CIEN.subtract(valor)).divide(CIEN, 4, RoundingMode.HALF_UP);
            case DOS_X_UNO -> precio.divide(BigDecimal.valueOf(2), 4, RoundingMode.HALF_UP);
            case PRECIO_FIJO -> valor.min(precio);
        };
    }

    /** Porcentaje de descuento equivalente (0-100). */
    public static BigDecimal porcentajeDescuento(TipoPromocion tipo, BigDecimal valor, BigDecimal precio) {
        if (precio.signum() == 0) return BigDecimal.ZERO;
        BigDecimal efectivo = precioEfectivoUnitario(tipo, valor, precio);
        return BigDecimal.ONE.subtract(efectivo.divide(precio, 6, RoundingMode.HALF_UP))
                .multiply(CIEN).setScale(2, RoundingMode.HALF_UP);
    }

    /** Descuento en dinero para una linea de venta. */
    public static BigDecimal descuentoLinea(TipoPromocion tipo, BigDecimal valor, BigDecimal precio, int cantidad) {
        BigDecimal d = switch (tipo) {
            case PORCENTAJE -> precio.multiply(BigDecimal.valueOf(cantidad)).multiply(valor).divide(CIEN, 2, RoundingMode.HALF_UP);
            case DOS_X_UNO -> precio.multiply(BigDecimal.valueOf(cantidad / 2));
            case PRECIO_FIJO -> precio.subtract(valor).max(BigDecimal.ZERO).multiply(BigDecimal.valueOf(cantidad));
        };
        return d.setScale(2, RoundingMode.HALF_UP);
    }

    /** Precio minimo permitido: costo x (1 + margenMinimo). */
    public static BigDecimal precioMinimo(BigDecimal costo, BigDecimal margenMinimo) {
        return costo.multiply(BigDecimal.ONE.add(margenMinimo)).setScale(4, RoundingMode.HALF_UP);
    }
}
