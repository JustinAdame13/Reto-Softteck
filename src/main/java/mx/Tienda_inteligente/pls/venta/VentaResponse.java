package mx.Tienda_inteligente.pls.venta;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record VentaResponse(
        Long id,
        String ticket,
        LocalDateTime fechaHora,
        Long productoId,
        String sku,
        String productoNombre,
        Integer cantidad,
        BigDecimal precioUnitario,
        BigDecimal descuentoAplicado,
        BigDecimal total,
        Long promocionId,
        Long empleadoId
) {
}
