package mx.Tienda_inteligente.pls.prediccion.motor;

import mx.Tienda_inteligente.pls.contexto.ContextoDto;

import java.time.LocalDate;
import java.util.List;

/**
 * CONTRATO con el motor de prediccion (interno en Java o externo en Python).
 * Se predicen los dias [fechaInicio, fechaInicio + dias - 1].
 * contexto trae un elemento por dia, desde el inicio del historial hasta el ultimo dia a predecir.
 */
public record EntradaPrediccion(
        LocalDate fechaInicio,
        int dias,
        List<ProductoHistorial> productos,
        List<ContextoDto> contexto
) {
}
