package mx.Tienda_inteligente.pls.common;

import java.time.LocalDate;

/** Rango de fechas inclusivo [desde, hasta] con valores por defecto. */
public record Fechas(LocalDate desde, LocalDate hasta) {

    public static Fechas rango(LocalDate desde, LocalDate hasta, int diasPorDefecto) {
        LocalDate h = hasta != null ? hasta : LocalDate.now();
        LocalDate d = desde != null ? desde : h.minusDays(diasPorDefecto - 1L);
        if (d.isAfter(h)) {
            throw new ReglaNegocioException("'desde' no puede ser posterior a 'hasta'");
        }
        return new Fechas(d, h);
    }

    public long dias() {
        return java.time.temporal.ChronoUnit.DAYS.between(desde, hasta) + 1;
    }

    /** Mismo largo, inmediatamente antes. */
    public Fechas anterior() {
        long n = dias();
        return new Fechas(desde.minusDays(n), desde.minusDays(1));
    }
}
