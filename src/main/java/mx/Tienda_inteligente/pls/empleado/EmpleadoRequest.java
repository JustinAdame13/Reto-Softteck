package mx.Tienda_inteligente.pls.empleado;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record EmpleadoRequest(
        @NotBlank @Size(max = 120) String nombre,
        @NotNull Rol rol,
        @NotNull @DecimalMin("0") BigDecimal costoHora,
        @Min(1) @Max(168) Integer horasMaxSemana,
        Boolean activo
) {
}
