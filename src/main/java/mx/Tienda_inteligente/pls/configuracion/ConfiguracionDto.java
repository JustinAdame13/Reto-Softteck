package mx.Tienda_inteligente.pls.configuracion;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import mx.Tienda_inteligente.pls.Enums.*;

import java.math.BigDecimal;
import java.time.LocalTime;

public record ConfiguracionDto(
        @NotBlank @Size(max = 100) String nombreSucursal,
        @Size(max = 100) String zona,
        @NotNull Tamano tamano,
        @NotNull LocalTime horaApertura,
        @NotNull LocalTime horaCierre,
        @NotNull @DecimalMin("0") @DecimalMax("1") BigDecimal margenMinimoPromo,
        @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal descuentoMaximo,
        @NotNull @Min(1) Integer mesesHistorico,
        @NotNull @Min(1) Integer transaccionesPorEmpleadoHora,

        /** Solo lectura en el PUT. Se cambia con PATCH /configuracion/ia */
        Enums.ProveedorIA proveedorIa,

        /** Solo lectura en el PUT. Null significa usar el modelo del .env */
        String modeloIa,

        /** Solo lectura: el proveedor elegido tiene API key y esta operando. */
        Boolean iaActiva
) {

    public static ConfiguracionDto from(Configuracion c, boolean iaActiva) {
        return new ConfiguracionDto(
                c.getNombreSucursal(), c.getZona(), c.getTamano(),
                c.getHoraApertura(), c.getHoraCierre(),
                c.getMargenMinimoPromo(), c.getDescuentoMaximo(),
                c.getMesesHistorico(), c.getTransaccionesPorEmpleadoHora(),
                c.getProveedorIa(), c.getModeloIa(), iaActiva);
    }
}