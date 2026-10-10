package mx.Tienda_inteligente.pls.common;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Utilidades para redondear montos y numeros de forma consistente. */
public final class Dinero {

    private Dinero() {
    }

    public static BigDecimal de(double valor) {
        return BigDecimal.valueOf(valor).setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal redondear(BigDecimal valor) {
        return valor == null ? null : valor.setScale(2, RoundingMode.HALF_UP);
    }

    public static double r2(double valor) {
        return Math.round(valor * 100.0) / 100.0;
    }

    public static BigDecimal nz(BigDecimal valor) {
        return valor == null ? BigDecimal.ZERO : valor;
    }
}
