package mx.Tienda_inteligente.pls.venta;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

/** Si no viene ticket se genera uno; si no viene fechaHora se usa la hora actual. */
public record TicketRequest(
        @Size(max = 30) String ticket,
        LocalDateTime fechaHora,
        Long empleadoId,
        @NotEmpty List<@Valid LineaTicketRequest> lineas
) {
}
