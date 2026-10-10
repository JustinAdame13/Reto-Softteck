package mx.Tienda_inteligente.pls.contexto;

import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Se usa como request y response. En PUT /contexto/{fecha} la fecha del cuerpo se ignora. */
public record ContextoDto(
        LocalDate fecha,
        Boolean esFestivo,
        @Size(max = 100) String nombreEvento,
        Boolean esQuincena,
        Temporada temporada,
        @Size(max = 50) String clima,
        BigDecimal temperatura
) {

    public static ContextoDto from(Contexto c) {
        return new ContextoDto(c.getFecha(), c.isEsFestivo(), c.getNombreEvento(), c.isEsQuincena(),
                c.getTemporada(), c.getClima(), c.getTemperatura());
    }

    /** true si el dia tiene algo especial (festivo o evento comercial). */
    public boolean tieneEvento() {
        return Boolean.TRUE.equals(esFestivo) || (nombreEvento != null && !nombreEvento.isBlank());
    }
}
