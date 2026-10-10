package mx.Tienda_inteligente.pls.configuracion;

import mx.Tienda_inteligente.pls.Infraestructura.RegistroLLM;
import mx.Tienda_inteligente.pls.common.RecursoNoEncontradoException;
import mx.Tienda_inteligente.pls.common.ReglaNegocioException;
import mx.Tienda_inteligente.pls.Enums.*;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class ConfiguracionService {

    private final ConfiguracionRepository repository;
    private final RegistroLLM registro;

    public ConfiguracionService(ConfiguracionRepository repository,
                                RegistroLLM registro) {
        this.repository = repository;
        this.registro = registro;
    }

    /** La usan los demas modulos (motores, promociones, plantillas). */
    @Transactional(readOnly = true)
    public Configuracion obtenerEntidad() {
        return repository.findById(Configuracion.ID_UNICO)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe la fila de configuracion (revisa la migracion V2)"));
    }

    /** true si hay un proveedor elegido Y ese proveedor tiene API key. */
    @Transactional(readOnly = true)
    public boolean iaActiva() {
        return registro.obtener(obtenerEntidad().getProveedorIa()).isPresent();
    }

    /** Proveedores con API key en el servidor, para el dropdown del frontend. */
    @Transactional(readOnly = true)
    public List<Enums.ProveedorIA> proveedoresDisponibles() {
        return registro.disponibles();
    }

    @Transactional(readOnly = true)
    public ConfiguracionDto obtener() {
        return ConfiguracionDto.from(obtenerEntidad(), iaActiva());
    }

    @Transactional
    public ConfiguracionDto actualizar(ConfiguracionDto dto) {
        if (!dto.horaApertura().isBefore(dto.horaCierre())) {
            throw new ReglaNegocioException(
                    "La hora de apertura debe ser antes de la hora de cierre",
                    Map.of("horaApertura", "Debe ser menor que horaCierre"));
        }

        Configuracion c = obtenerEntidad();
        c.setNombreSucursal(dto.nombreSucursal());
        c.setZona(dto.zona());
        c.setTamano(dto.tamano());
        c.setHoraApertura(dto.horaApertura());
        c.setHoraCierre(dto.horaCierre());
        c.setMargenMinimoPromo(dto.margenMinimoPromo());
        c.setDescuentoMaximo(dto.descuentoMaximo());
        c.setMesesHistorico(dto.mesesHistorico());
        c.setTransaccionesPorEmpleadoHora(dto.transaccionesPorEmpleadoHora());

        // La IA no se toca aqui a proposito: tiene su propio endpoint
        // con su propia validacion de API key.

        return ConfiguracionDto.from(repository.save(c), iaActiva());
    }

    @Transactional
    public ConfiguracionDto cambiarIa(Enums.ProveedorIA proveedor, String modelo) {

        if (proveedor == null) {
            throw new ReglaNegocioException(
                    "proveedorIa es obligatorio",
                    Map.of("proveedorIa", "Valores: NINGUNO, ANTHROPIC, OPENAI, GEMINI"));
        }

        // Prender un proveedor sin llave dejaria el sistema cayendo
        // al motor BASE en silencio. Mejor rechazarlo de frente.
        if (proveedor != Enums.ProveedorIA.NINGUNO
                && registro.obtener(proveedor).isEmpty()) {
            throw new ReglaNegocioException(
                    "El proveedor " + proveedor + " no tiene API key configurada en el servidor",
                    Map.of("proveedorIa", "Disponibles: " + registro.disponibles()));
        }

        Configuracion c = obtenerEntidad();
        c.setProveedorIa(proveedor);
        c.setModeloIa(modelo);
        return ConfiguracionDto.from(repository.save(c), iaActiva());
    }
}