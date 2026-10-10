package mx.Tienda_inteligente.pls.prediccion;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record PrediccionResponse(
        Long id,
        Long productoId,
        LocalDate fechaObjetivo,
        BigDecimal cantidadPredicha,
        BigDecimal limiteInferior,
        BigDecimal limiteSuperior,
        Integer stockRecomendado,
        Integer cantidadAPedir,
        ModeloPrediccion modelo,
        LocalDateTime generadaEn
) {
    public static PrediccionResponse from(PrediccionDemanda p) {
        return new PrediccionResponse(p.getId(), p.getProductoId(), p.getFechaObjetivo(), p.getCantidadPredicha(),
                p.getLimiteInferior(), p.getLimiteSuperior(), p.getStockRecomendado(), p.getCantidadAPedir(),
                p.getModelo(), p.getGeneradaEn());
    }
}
