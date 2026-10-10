package mx.Tienda_inteligente.pls.configuracion;

import jakarta.validation.constraints.NotNull;

public record IaSwitchRequest(@NotNull Boolean iaHabilitada) {
}
