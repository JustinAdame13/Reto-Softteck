package mx.Tienda_inteligente.pls.producto;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProductoRepository extends JpaRepository<Producto, Long> {

    Optional<Producto> findBySku(String sku);

    boolean existsBySku(String sku);

    List<Producto> findBySkuIn(Collection<String> skus);

    List<Producto> findByActivoTrueOrderByNombre();

    @Query("select distinct p.categoria from Producto p order by p.categoria")
    List<String> categorias();

    /** Suma (o resta, si delta es negativo) al stock de forma atomica. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Producto p set p.stockActual = p.stockActual + :delta where p.id = :id")
    int sumarStock(@Param("id") Long id, @Param("delta") int delta);
}
