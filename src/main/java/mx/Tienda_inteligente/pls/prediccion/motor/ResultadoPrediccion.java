package mx.Tienda_inteligente.pls.prediccion.motor;

import java.time.LocalDate;

/**
 * CONTRATO: lo que regresa el motor por producto y dia.
 * El backend calcula despues stockRecomendado y cantidadAPedir (eso es logica de inventario, no del motor).
 */
public record ResultadoPrediccion(
        Long productoId,
        LocalDate fechaObjetivo,
        double cantidadPredicha,
        double limiteInferior,
        double limiteSuperior
) {
}
