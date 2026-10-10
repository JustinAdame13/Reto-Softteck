package mx.Tienda_inteligente.pls.prediccion;

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
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "prediccion_demanda")
@Getter
@Setter
@NoArgsConstructor
public class PrediccionDemanda {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "producto_id", nullable = false)
    private Long productoId;

    @Column(name = "fecha_objetivo", nullable = false)
    private LocalDate fechaObjetivo;

    @Column(name = "cantidad_predicha", nullable = false, precision = 12, scale = 2)
    private BigDecimal cantidadPredicha;

    @Column(name = "limite_inferior", precision = 12, scale = 2)
    private BigDecimal limiteInferior;

    @Column(name = "limite_superior", precision = 12, scale = 2)
    private BigDecimal limiteSuperior;

    @Column(name = "stock_recomendado")
    private Integer stockRecomendado;

    @Column(name = "cantidad_a_pedir")
    private Integer cantidadAPedir;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 5)
    private ModeloPrediccion modelo;

    @Column(name = "generada_en", nullable = false)
    private LocalDateTime generadaEn;
}
