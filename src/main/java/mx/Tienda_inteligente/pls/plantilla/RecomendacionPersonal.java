package mx.Tienda_inteligente.pls.plantilla;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Resultado de /plantillas/recomendar.
 * demandaPorHora: tickets promedio por dia de semana (1-7) y hora ("08", "09", ...).
 */
public record RecomendacionPersonal(
        int semanasAnalizadas,
        int transaccionesPorEmpleadoHora,
        boolean guardado,
        List<PlantillaResponse> bloques,
        Map<String, Double> horasPersonaPorRol,
        BigDecimal costoSemanalEstimado,
        Map<Integer, Map<String, Double>> demandaPorHora,
        List<String> notas
) {
}
