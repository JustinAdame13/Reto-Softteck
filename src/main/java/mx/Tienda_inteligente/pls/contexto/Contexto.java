package mx.Tienda_inteligente.pls.contexto;

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

@Entity
@Table(name = "contexto")
@Getter
@Setter
@NoArgsConstructor
public class Contexto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private LocalDate fecha;

    @Column(name = "es_festivo", nullable = false)
    private boolean esFestivo;

    @Column(name = "nombre_evento", length = 100)
    private String nombreEvento;

    @Column(name = "es_quincena", nullable = false)
    private boolean esQuincena;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private Temporada temporada = Temporada.NORMAL;

    @Column(length = 50)
    private String clima;

    @Column(precision = 4, scale = 1)
    private BigDecimal temperatura;
}
