package mx.Tienda_inteligente.pls.configuracion;

import mx.Tienda_inteligente.pls.common.RecursoNoEncontradoException;
import mx.Tienda_inteligente.pls.common.ReglaNegocioException;
import mx.Tienda_inteligente.pls.ia.ClienteIa;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
public class ConfiguracionService {

    private final ConfiguracionRepository repository;
    private final ClienteIa clienteIa;

    public ConfiguracionService(ConfiguracionRepository repository, ClienteIa clienteIa) {
        this.repository = repository;
        this.clienteIa = clienteIa;
    }

    /** La usan los demas modulos (motores, promociones, plantillas). */
    @Transactional(readOnly = true)
    public Configuracion obtenerEntidad() {
        return repository.findById(Configuracion.ID_UNICO)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe la fila de configuracion (revisa la migracion V2)"));
    }

    /** true si el switch esta prendido Y hay API key: es lo que decide el modo AUTO. */
    @Transactional(readOnly = true)
    public boolean iaActiva() {
        return obtenerEntidad().isIaHabilitada() && clienteIa.disponible();
    }

    @Transactional(readOnly = true)
    public ConfiguracionDto obtener() {
        return ConfiguracionDto.from(obtenerEntidad(), clienteIa.disponible());
    }

    @Transactional
    public ConfiguracionDto actualizar(ConfiguracionDto dto) {
        if (!dto.horaApertura().isBefore(dto.horaCierre())) {
            throw new ReglaNegocioException("La hora de apertura debe ser antes de la hora de cierre",
                    Map.of("horaApertura", "Debe ser menor que horaCierre"));
        }
        Configuracion c = obtenerEntidad();
        c.setNombreSucursal(dto.nombreSucursal());
        c.setZona(dto.zona());
        c.setTamano(dto.tamano());
        c.setHoraApertura(dto.horaApertura());
        c.setHoraCierre(dto.horaCierre());
        if (dto.iaHabilitada() != null) {
            c.setIaHabilitada(dto.iaHabilitada());
        }
        c.setMargenMinimoPromo(dto.margenMinimoPromo());
        c.setDescuentoMaximo(dto.descuentoMaximo());
        c.setMesesHistorico(dto.mesesHistorico());
        c.setTransaccionesPorEmpleadoHora(dto.transaccionesPorEmpleadoHora());
        return ConfiguracionDto.from(repository.save(c), clienteIa.disponible());
    }

    @Transactional
    public ConfiguracionDto cambiarIa(boolean habilitada) {
        Configuracion c = obtenerEntidad();
        c.setIaHabilitada(habilitada);
        return ConfiguracionDto.from(repository.save(c), clienteIa.disponible());
    }
}
