package mx.Tienda_inteligente.pls.prediccion;

import mx.Tienda_inteligente.pls.prediccion.motor.CalibracionAyax;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record GeneracionResponse(
        LocalDate fechaInicio,
        LocalDate fechaFin,
        List<ModeloPrediccion> modelos,
        int productos,
        int totalPredicciones,
        /** "AYAX" (algoritmo propio) o "EXTERNO" (motor en Python). */
        String motorBase,
        List<String> advertencias,

        /**
         * productoId -> por que la IA ajusto ese producto. Vacio si no se uso IA.
         * No se guarda en la tabla: es informativo para el frontend y para la demo.
         */
        Map<Long, String> explicacionesIa,

        /**
         * productoId -> como se autocalibro Ayax (metodo, parametros y error de validacion).
         * Vacio si el pronostico BASE vino del motor externo.
         */
        Map<Long, CalibracionAyax> calibracionAyax,

        List<PrediccionResponse> predicciones
) {
}
