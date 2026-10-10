package mx.Tienda_inteligente.pls.basedatos;

import jakarta.validation.constraints.NotNull;
import mx.Tienda_inteligente.pls.configuracion.Tamano;

/** Body de PUT /api/v1/sucursal-activa: { "tamano": "GRANDE" } */
public record CambioBaseRequest(@NotNull Tamano tamano) {
}
