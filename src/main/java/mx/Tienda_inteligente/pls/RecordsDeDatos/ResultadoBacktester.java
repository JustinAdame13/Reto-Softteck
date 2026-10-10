package mx.Tienda_inteligente.pls.RecordsDeDatos;

import mx.Tienda_inteligente.pls.Enums.*;

public record ResultadoBacktester (
    Long productoId,
    int diasEvaluados,
    double mape,              // Error % promedio: 15.0 = se equivoca 15%
    double mae,               // Error en unidades: 3.2 = falla por 3 piezas
    double sesgo,             // + sobreestima, − subestima
    int vecesQuedoCorto,      // Predijo menos → riesgo de quiebre
    int vecesSobro,           // Predijo más → riesgo de sobrestock
    Enums.CalidadPrediccion calidad
) {
        public static ResultadoBacktester sinDatos(Long productoId) {
            return new ResultadoBacktester(
                    productoId, 0, 0, 0, 0, 0, 0,
                    Enums.CalidadPrediccion.SIN_HISTORIAL
            );
        }
    }