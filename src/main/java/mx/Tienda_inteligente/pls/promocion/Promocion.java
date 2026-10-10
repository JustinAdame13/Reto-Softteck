package mx.Tienda_inteligente.pls.promocion;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "promociones")
@Getter
@Setter
@NoArgsConstructor
public class Promocion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "producto_id", nullable = false)
    private Long productoId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private TipoPromocion tipo;

    /** PORCENTAJE: 25 = 25 %. PRECIO_FIJO: precio final. DOS_X_UNO: se ignora (0). */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal valor;

    @Column(name = "fecha_inicio", nullable = false)
    private LocalDate fechaInicio;

    @Column(name = "fecha_fin", nullable = false)
    private LocalDate fechaFin;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private EstadoPromocion estado = EstadoPromocion.SUGERIDA;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private OrigenPromocion origen;

    @Enumerated(EnumType.STRING)
    @Column(length = 15)
    private MotivoPromocion motivo;

    @Column(columnDefinition = "TEXT")
    private String explicacion;

    @Column(name = "ventas_esperadas")
    private Integer ventasEsperadas;

    @Column(name = "ventas_reales")
    private Integer ventasReales;

    @Column(name = "ganancia_esperada", precision = 12, scale = 2)
    private BigDecimal gananciaEsperada;

    @Column(name = "creada_en", nullable = false, updatable = false)
    private LocalDateTime creadaEn;

    @PrePersist
    void alCrear() {
        if (creadaEn == null) {
            creadaEn = LocalDateTime.now();
        }
    }

    public boolean vigenteEn(LocalDate fecha) {
        return !fecha.isBefore(fechaInicio) && !fecha.isAfter(fechaFin);
    }
}
