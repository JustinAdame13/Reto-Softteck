package mx.Tienda_inteligente.pls.plantilla;

import mx.Tienda_inteligente.pls.empleado.Rol;

/**
 * Personal requerido vs disponible por rol.
 * horasRequeridas: suma semanal de personal x horas. horasDisponibles: suma de horasMaxSemana de activos.
 * picoSimultaneo: maximo de personas requeridas al mismo tiempo.
 */
public record CoberturaRol(
        Rol rol,
        double horasRequeridas,
        double horasDisponibles,
        double deficitHoras,
        int picoSimultaneo,
        int empleadosActivos,
        int empleadosFaltantes,
        String estado
) {
}
