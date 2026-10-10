package mx.Tienda_inteligente.pls.promocion;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface PromocionRepository extends JpaRepository<Promocion, Long> {

    List<Promocion> findByEstado(EstadoPromocion estado);

    /** Promociones de un producto en ciertos estados que se traslapan con [inicio, fin]. */
    @Query("""
            select p from Promocion p
            where p.productoId = :productoId
              and p.estado in :estados
              and p.fechaInicio <= :fin and p.fechaFin >= :inicio
            """)
    List<Promocion> traslapadas(@Param("productoId") Long productoId,
                                @Param("estados") Collection<EstadoPromocion> estados,
                                @Param("inicio") LocalDate inicio,
                                @Param("fin") LocalDate fin);

    /** ACTIVAS vigentes en una fecha, para aplicar el descuento en la caja. */
    @Query("""
            select p from Promocion p
            where p.productoId in :productoIds
              and p.estado = mx.Tienda_inteligente.pls.promocion.EstadoPromocion.ACTIVA
              and p.fechaInicio <= :fecha and p.fechaFin >= :fecha
            """)
    List<Promocion> activasEn(@Param("productoIds") Collection<Long> productoIds, @Param("fecha") LocalDate fecha);

    /** Borra sugerencias viejas del motor que nadie acepto (nunca tienen ventas ligadas). */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            delete from Promocion p
            where p.estado = mx.Tienda_inteligente.pls.promocion.EstadoPromocion.SUGERIDA
              and p.origen <> mx.Tienda_inteligente.pls.promocion.OrigenPromocion.MANUAL
            """)
    int borrarSugerenciasAutomaticas();
}
