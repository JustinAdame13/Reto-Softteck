package mx.Tienda_inteligente.pls.empleado;

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

import java.math.BigDecimal;

@Entity
@Table(name = "empleados")
@Getter
@Setter
@NoArgsConstructor
public class Empleado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String nombre;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Rol rol;

    @Column(name = "costo_hora", nullable = false, precision = 10, scale = 2)
    private BigDecimal costoHora;

    @Column(name = "horas_max_semana", nullable = false)
    private Integer horasMaxSemana = 48;

    @Column(nullable = false)
    private boolean activo = true;
}
