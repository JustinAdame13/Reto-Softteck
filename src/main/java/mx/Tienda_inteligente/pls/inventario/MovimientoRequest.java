package mx.Tienda_inteligente.pls.inventario;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * cantidad llega positiva y el backend decide el signo segun el tipo (AJUSTE acepta negativos).
 * Si no viene costoTotal se calcula como |cantidad| x costo del producto.
 */
public record MovimientoRequest(
        @NotNull Long productoId,
        @NotNull TipoMovimiento tipo,
        @NotNull Integer cantidad,
        @DecimalMin("0") BigDecimal costoTotal,
        @Size(max = 255) String motivo,
        LocalDateTime fecha
) {
}
