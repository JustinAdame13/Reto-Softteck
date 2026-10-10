package mx.Tienda_inteligente.pls.configuracion;

import jakarta.validation.constraints.NotNull;
import mx.Tienda_inteligente.pls.Enums.*;

public record IaSwitchRequest(

        @NotNull(message = "proveedorIa es obligatorio")
        Enums.ProveedorIA proveedorIa,

        /** Opcional: si viene null se usa el modelo del .env. */
        String modeloIa
) {}