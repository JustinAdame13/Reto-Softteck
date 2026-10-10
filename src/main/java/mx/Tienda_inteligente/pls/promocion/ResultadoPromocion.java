package mx.Tienda_inteligente.pls.promocion;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * ROI de una promocion. ventasSinPromoEstimadas = promedio diario de las 4 semanas previas x dias de promo.
 * roi = gananciaIncremental / descuentoOtorgado.
 */
public record ResultadoPromocion(
        Long promocionId,
        Long productoId,
        EstadoPromocion estado,
        LocalDate fechaInicio,
        LocalDate fechaFin,
        long diasEvaluados,
        Integer ventasEsperadas,
        int ventasReales,
        double ventasSinPromoEstimadas,
        double incrementoVentasPorcentaje,
        BigDecimal ingresos,
        BigDecimal descuentoOtorgado,
        BigDecimal margenReal,
        BigDecimal margenSinPromoEstimado,
        BigDecimal gananciaIncremental,
        BigDecimal gananciaEsperada,
        Double roi,
        Double cumplimientoPorcentaje
) {
}
