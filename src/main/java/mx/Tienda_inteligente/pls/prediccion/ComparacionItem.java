package mx.Tienda_inteligente.pls.prediccion;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDate;

/** BASE vs IA lado a lado; real solo viene en fechas que ya pasaron. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ComparacionItem(
        LocalDate fecha,
        Double real,
        Double base,
        Double baseMin,
        Double baseMax,
        Double ia,
        Double iaMin,
        Double iaMax
) {
}
