package mx.Tienda_inteligente.pls.configuracion;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import mx.Tienda_inteligente.pls.Enums.Enums;

import java.math.BigDecimal;
import java.time.LocalTime;

/** Una sola fila (id = 1) creada por la migracion V2. */
@Entity
@Table(name = "configuracion")
@Getter
@Setter
@NoArgsConstructor
public class Configuracion {

    public static final short ID_UNICO = 1;

    @Id
    private Short id; // SMALLINT en la BD -> Short

    @Column(name = "nombre_sucursal", nullable = false, length = 100)
    private String nombreSucursal;

    @Column(length = 100)
    private String zona;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Tamano tamano;

    @Column(name = "hora_apertura", nullable = false)
    private LocalTime horaApertura;

    @Column(name = "hora_cierre", nullable = false)
    private LocalTime horaCierre;

    @Enumerated(EnumType.STRING)
    @Column(name = "proveedor_ia", nullable = false, length = 20)
    private Enums.ProveedorIA proveedorIa = Enums.ProveedorIA.NINGUNO;

    @Column(name = "modelo_ia", length = 80)
    private String modeloIa;

    /** Conveniencia para el frontend y para los if del servicio. */
    @Transient
    public boolean isIaActiva() {
        return proveedorIa != null
                && proveedorIa != Enums.ProveedorIA.NINGUNO;
    }

    /** Fraccion: 0.10 = 10 % sobre el costo. */
    @Column(name = "margen_minimo_promo", nullable = false, precision = 5, scale = 4)
    private BigDecimal margenMinimoPromo;

    /** Porcentaje: 30 = 30 %. */
    @Column(name = "descuento_maximo", nullable = false, precision = 5, scale = 2)
    private BigDecimal descuentoMaximo;

    @Column(name = "meses_historico", nullable = false)
    private Integer mesesHistorico;

    @Column(name = "transacciones_por_empleado_hora", nullable = false)
    private Integer transaccionesPorEmpleadoHora;
}
