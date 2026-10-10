package mx.Tienda_inteligente.pls.common;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

/**
 * Formato unico de error que regresa toda la API.
 * Ejemplo: { "status": 422, "error": "REGLA_NEGOCIO", "mensaje": "...", "campos": { "valor": "..." } }
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ApiError(int status, String error, String mensaje, Map<String, String> campos) {

    public static ApiError of(int status, String error, String mensaje) {
        return new ApiError(status, error, mensaje, Map.of());
    }
}
