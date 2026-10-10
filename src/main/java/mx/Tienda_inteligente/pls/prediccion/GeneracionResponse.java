package mx.Tienda_inteligente.pls.prediccion;

import java.time.LocalDate;
import java.util.List;

public record GeneracionResponse(
        LocalDate fechaInicio,
        LocalDate fechaFin,
        List<ModeloPrediccion> modelos,
        int productos,
        int totalPredicciones,
        String motorBase,
        List<String> advertencias,
        List<PrediccionResponse> predicciones
) {
}
