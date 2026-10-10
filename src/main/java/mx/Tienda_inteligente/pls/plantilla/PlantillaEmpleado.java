package mx.Tienda_inteligente.pls.plantilla;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import mx.Tienda_inteligente.pls.empleado.Rol;

import java.time.LocalTime;

/** Cuanta gente se necesita por bloque de horario (no quien). */
@Entity
@Table(name = "plantillas_empleados")
@Getter
@Setter
@NoArgsConstructor
public class PlantillaEmpleado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 1 = lunes ... 7 = domingo. SMALLINT en la BD -> Short. */
    @Column(name = "dia_semana", nullable = false)
    private Short diaSemana;

    @Column(name = "hora_inicio", nullable = false)
    private LocalTime horaInicio;

    @Column(name = "hora_fin", nullable = false)
    private LocalTime horaFin;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Rol rol;

    @Column(name = "personal_requerido", nullable = false)
    private Integer personalRequerido;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private OrigenPlantilla origen = OrigenPlantilla.MANUAL;

    public double horas() {
        return java.time.Duration.between(horaInicio, horaFin).toMinutes() / 60.0;
    }
}
