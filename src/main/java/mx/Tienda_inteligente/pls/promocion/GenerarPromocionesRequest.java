package mx.Tienda_inteligente.pls.promocion;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Todos los campos son opcionales.
 * modelo: AUTO (respeta el switch de IA), BASE (solo algoritmo) o IA.
 * reemplazar: borra las sugerencias automaticas anteriores que nadie acepto (defecto true).
 */
public record GenerarPromocionesRequest(
        @Min(1) @Max(50) Integer maxSugerencias,
        @Min(1) @Max(60) Integer horizonteDias,
        String modelo,
        Boolean reemplazar
) {
}
