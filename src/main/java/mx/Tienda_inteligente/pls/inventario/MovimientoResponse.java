package mx.Tienda_inteligente.pls.inventario;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record MovimientoResponse(
        Long id,
        Long productoId,
        String sku,
        String productoNombre,
        LocalDateTime fecha,
        TipoMovimiento tipo,
        Integer cantidad,
        Integer efectoEnStock,
        BigDecimal costoTotal,
        String motivo,
        Integer stockResultante
) {
}
