package mx.Tienda_inteligente.pls.venta;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Fila historica para /ventas/lote (NO toca el stock).
 * Se puede mandar productoId o sku. Si no viene precioUnitario se usa el precio actual del producto.
 */
public record VentaLoteRequest(
        @NotBlank @Size(max = 30) String ticket,
        @NotNull LocalDateTime fechaHora,
        Long productoId,
        String sku,
        @NotNull @Min(1) Integer cantidad,
        @DecimalMin("0") BigDecimal precioUnitario,
        @DecimalMin("0") BigDecimal descuentoAplicado,
        Long promocionId,
        Long empleadoId
) {
}
