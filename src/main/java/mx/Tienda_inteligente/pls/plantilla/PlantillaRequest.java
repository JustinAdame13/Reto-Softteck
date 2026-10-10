package mx.Tienda_inteligente.pls.plantilla;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import mx.Tienda_inteligente.pls.empleado.Rol;

import java.time.LocalTime;

public record PlantillaRequest(
        @NotNull @Min(1) @Max(7) Integer diaSemana,
        @NotNull LocalTime horaInicio,
        @NotNull LocalTime horaFin,
        @NotNull Rol rol,
        @NotNull @Min(0) Integer personalRequerido
) {
}
