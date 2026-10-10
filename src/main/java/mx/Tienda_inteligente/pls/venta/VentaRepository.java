package mx.Tienda_inteligente.pls.venta;

import org.springframework.data.jpa.repository.JpaRepository;

public interface VentaRepository extends JpaRepository<Venta, Long> {

    boolean existsByTicket(String ticket);
}
