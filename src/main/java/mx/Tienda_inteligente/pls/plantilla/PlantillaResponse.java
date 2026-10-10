package mx.Tienda_inteligente.pls.plantilla;

import mx.Tienda_inteligente.pls.empleado.Rol;

import java.time.LocalTime;

public record PlantillaResponse(
        Long id,
        int diaSemana,
        String dia,
        LocalTime horaInicio,
        LocalTime horaFin,
        Rol rol,
        int personalRequerido,
        OrigenPlantilla origen,
        double horasPersona
) {
    static final String[] DIAS = {"", "Lunes", "Martes", "Miercoles", "Jueves", "Viernes", "Sabado", "Domingo"};

    public static PlantillaResponse from(PlantillaEmpleado p) {
        return new PlantillaResponse(p.getId(), p.getDiaSemana(), DIAS[p.getDiaSemana()], p.getHoraInicio(),
                p.getHoraFin(), p.getRol(), p.getPersonalRequerido(), p.getOrigen(),
                Math.round(p.horas() * p.getPersonalRequerido() * 100) / 100.0);
    }
}
