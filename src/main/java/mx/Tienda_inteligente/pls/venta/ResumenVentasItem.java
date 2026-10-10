package mx.Tienda_inteligente.pls.venta;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;

/** Una fila de /ventas/resumen. etiqueta solo viene en agrupaciones por producto o dia de la semana. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ResumenVentasItem(
        String periodo,
        String etiqueta,
        long unidades,
        BigDecimal ingresos,
        BigDecimal costo,
        BigDecimal margen,
        long tickets
) {
}
