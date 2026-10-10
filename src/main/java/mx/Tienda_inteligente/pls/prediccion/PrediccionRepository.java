package mx.Tienda_inteligente.pls.prediccion;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;

public interface PrediccionRepository extends JpaRepository<PrediccionDemanda, Long> {

    /** Regenerar reemplaza: primero se borran las del mismo producto, rango y modelo. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            delete from PrediccionDemanda p
            where p.productoId in :productoIds and p.modelo in :modelos
              and p.fechaObjetivo between :desde and :hasta
            """)
    int borrar(@Param("productoIds") Collection<Long> productoIds,
               @Param("modelos") Collection<ModeloPrediccion> modelos,
               @Param("desde") LocalDate desde,
               @Param("hasta") LocalDate hasta);
}
