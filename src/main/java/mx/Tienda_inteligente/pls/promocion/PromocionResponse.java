package mx.Tienda_inteligente.pls.promocion;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record PromocionResponse(
        Long id,
        Long productoId,
        String sku,
        String productoNombre,
        TipoPromocion tipo,
        BigDecimal valor,
        LocalDate fechaInicio,
        LocalDate fechaFin,
        EstadoPromocion estado,
        OrigenPromocion origen,
        MotivoPromocion motivo,
        Integer ventasEsperadas,
        Integer ventasReales,
        BigDecimal gananciaEsperada,
        BigDecimal precioNormal,
        BigDecimal precioPromocion,
        BigDecimal descuentoPorcentaje,
        String explicacion,
        LocalDateTime creadaEn
) {
}
