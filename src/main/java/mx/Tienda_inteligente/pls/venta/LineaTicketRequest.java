package mx.Tienda_inteligente.pls.venta;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/** Si no viene precioUnitario se usa el precioVenta del producto. */
public record LineaTicketRequest(
        @NotNull Long productoId,
        @NotNull @Min(1) Integer cantidad,
        @DecimalMin("0") BigDecimal precioUnitario
) {
}
