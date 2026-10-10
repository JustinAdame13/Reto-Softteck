package mx.Tienda_inteligente.pls.configuracion;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalTime;

public record ConfiguracionDto(
        @NotBlank @Size(max = 100) String nombreSucursal,
        @Size(max = 100) String zona,
        @NotNull Tamano tamano,
        @NotNull LocalTime horaApertura,
        @NotNull LocalTime horaCierre,
        Boolean iaHabilitada,
        @NotNull @DecimalMin("0") @DecimalMax("1") BigDecimal margenMinimoPromo,
        @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal descuentoMaximo,
        @NotNull @Min(1) Integer mesesHistorico,
        @NotNull @Min(1) Integer transaccionesPorEmpleadoHora,
        /** Solo lectura: indica si hay API key de IA configurada en el servidor. */
        Boolean iaDisponible
) {

    public static ConfiguracionDto from(Configuracion c, boolean iaDisponible) {
        return new ConfiguracionDto(c.getNombreSucursal(), c.getZona(), c.getTamano(),
                c.getHoraApertura(), c.getHoraCierre(), c.isIaHabilitada(),
                c.getMargenMinimoPromo(), c.getDescuentoMaximo(),
                c.getMesesHistorico(), c.getTransaccionesPorEmpleadoHora(), iaDisponible);
    }
}
