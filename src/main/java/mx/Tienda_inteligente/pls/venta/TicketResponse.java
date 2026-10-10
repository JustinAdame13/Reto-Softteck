package mx.Tienda_inteligente.pls.venta;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record TicketResponse(
        String ticket,
        LocalDateTime fechaHora,
        Long empleadoId,
        List<VentaResponse> lineas,
        BigDecimal subtotal,
        BigDecimal descuento,
        BigDecimal total
) {
}
