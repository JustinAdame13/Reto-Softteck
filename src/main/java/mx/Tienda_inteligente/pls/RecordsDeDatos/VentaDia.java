package mx.Tienda_inteligente.pls.RecordsDeDatos;

import java.math.BigDecimal;
import java.time.LocalDate;

public record VentaDia(
        LocalDate fecha,
        int unidades,
        BigDecimal ingresoTotal,
        boolean tuvoPromocion,    // Sin esto, sesga los promedios
        boolean huboQuiebreStock  // Sin esto, también sesga
) {}



