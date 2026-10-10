package mx.Tienda_inteligente.pls.promocion;

import jakarta.validation.constraints.NotNull;

public record EstadoRequest(@NotNull EstadoPromocion estado) {
}
