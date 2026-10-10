package mx.Tienda_inteligente.pls.empleado;

import java.math.BigDecimal;

public record EmpleadoResponse(Long id, String nombre, Rol rol, BigDecimal costoHora,
                               Integer horasMaxSemana, boolean activo) {

    public static EmpleadoResponse from(Empleado e) {
        return new EmpleadoResponse(e.getId(), e.getNombre(), e.getRol(), e.getCostoHora(),
                e.getHorasMaxSemana(), e.isActivo());
    }
}
