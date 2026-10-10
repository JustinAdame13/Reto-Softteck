package mx.Tienda_inteligente.pls.empleado;

import jakarta.validation.constraints.NotNull;

public record ActivoRequest(@NotNull Boolean activo) {
}
