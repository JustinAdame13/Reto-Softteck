package mx.Tienda_inteligente.pls.producto;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "productos")
@Getter
@Setter
@NoArgsConstructor
public class Producto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50, unique = true)
    private String sku;

    @Column(nullable = false, length = 150)
    private String nombre;

    @Column(nullable = false, length = 80)
    private String categoria;

    @Column(name = "precio_venta", nullable = false, precision = 12, scale = 2)
    private BigDecimal precioVenta;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal costo;

    /** Solo cambia por ventas y movimientos de inventario (nunca por PUT). */
    @Column(name = "stock_actual", nullable = false)
    private Integer stockActual = 0;

    @Column(name = "stock_minimo", nullable = false)
    private Integer stockMinimo = 0;

    @Column(name = "stock_maximo")
    private Integer stockMaximo;

    @Column(name = "lead_time_dias", nullable = false)
    private Integer leadTimeDias = 1;

    @Column(name = "es_perecedero", nullable = false)
    private boolean esPerecedero;

    @Column(name = "vida_util_dias")
    private Integer vidaUtilDias;

    @Column(nullable = false)
    private boolean activo = true;
}
