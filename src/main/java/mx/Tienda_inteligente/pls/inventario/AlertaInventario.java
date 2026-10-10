package mx.Tienda_inteligente.pls.inventario;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AlertaInventario(
        Long productoId,
        String sku,
        String nombre,
        TipoAlerta tipo,
        String severidad,
        Integer stockActual,
        Integer puntoReorden,
        Integer cantidadSugerida,
        Integer diasRestantes,
        Integer unidadesEnRiesgo,
        BigDecimal perdidaPotencial,
        String mensaje
) {
    public enum TipoAlerta { AGOTADO, BAJO_STOCK, SOBRESTOCK, POR_CADUCAR, SIN_MOVIMIENTO }
}
