package mx.Tienda_inteligente.pls.promocion;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Promocion manual. valor: PORCENTAJE = % de descuento, PRECIO_FIJO = precio final, DOS_X_UNO = se ignora.
 * activar = true la crea directamente ACTIVA (si no, queda SUGERIDA para que el gerente la acepte).
 */
public record PromocionRequest(
        @NotNull Long productoId,
        @NotNull TipoPromocion tipo,
        @DecimalMin("0") BigDecimal valor,
        @NotNull LocalDate fechaInicio,
        @NotNull LocalDate fechaFin,
        MotivoPromocion motivo,
        String explicacion,
        Integer ventasEsperadas,
        BigDecimal gananciaEsperada,
        Boolean activar
) {
}
