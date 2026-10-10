package mx.Tienda_inteligente.pls.plantilla;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PlantillaRepository extends JpaRepository<PlantillaEmpleado, Long> {

    List<PlantillaEmpleado> findAllByOrderByDiaSemanaAscHoraInicioAsc();

    List<PlantillaEmpleado> findByOrigen(OrigenPlantilla origen);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from PlantillaEmpleado p where p.origen = :origen")
    int borrarPorOrigen(@Param("origen") OrigenPlantilla origen);
}
