package mx.Tienda_inteligente.pls.producto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** Cuerpo de POST y PUT. stockActual solo se usa en POST (stock inicial). */
public record ProductoRequest(
        @NotBlank @Size(max = 50) String sku,
        @NotBlank @Size(max = 150) String nombre,
        @NotBlank @Size(max = 80) String categoria,
        @NotNull @DecimalMin("0") BigDecimal precioVenta,
        @NotNull @DecimalMin("0") BigDecimal costo,
        @Min(0) Integer stockActual,
        @Min(0) Integer stockMinimo,
        @Min(0) Integer stockMaximo,
        @Min(0) Integer leadTimeDias,
        Boolean esPerecedero,
        @Min(1) Integer vidaUtilDias
) {
}
