package mx.Tienda_inteligente.pls.contexto;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ContextoRepository extends JpaRepository<Contexto, Long> {

    Optional<Contexto> findByFecha(LocalDate fecha);

    List<Contexto> findByFechaBetweenOrderByFecha(LocalDate desde, LocalDate hasta);
}
