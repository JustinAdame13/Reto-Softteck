package mx.Tienda_inteligente.pls.prediccion;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record GeneracionResponse(
        LocalDate fechaInicio,
        LocalDate fechaFin,
        List<ModeloPrediccion> modelos,
        int productos,
        int totalPredicciones,
        String motorBase,
        List<String> advertencias,

        /**
         * productoId -> por que la IA ajusto ese producto. Vacio si no se uso IA.
         * No se guarda en la tabla: es informativo para el frontend y para la demo.
         */
        Map<Long, String> explicacionesIa,

        List<PrediccionResponse> predicciones
) {
}
