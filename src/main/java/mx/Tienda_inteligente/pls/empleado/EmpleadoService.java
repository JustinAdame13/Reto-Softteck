package mx.Tienda_inteligente.pls.empleado;

import mx.Tienda_inteligente.pls.common.RecursoNoEncontradoException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
public class EmpleadoService {

    private final EmpleadoRepository repository;

    public EmpleadoService(EmpleadoRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<EmpleadoResponse> listar(Rol rol, Boolean activo) {
        return repository.findAll().stream()
                .filter(e -> rol == null || e.getRol() == rol)
                .filter(e -> activo == null || e.isActivo() == activo)
                .sorted(Comparator.comparing(Empleado::getNombre, String.CASE_INSENSITIVE_ORDER))
                .map(EmpleadoResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public EmpleadoResponse obtener(Long id) {
        return EmpleadoResponse.from(obtenerEntidad(id));
    }

    @Transactional(readOnly = true)
    public Empleado obtenerEntidad(Long id) {
        return repository.findById(id).orElseThrow(() -> RecursoNoEncontradoException.de("Empleado", id));
    }

    @Transactional
    public EmpleadoResponse crear(EmpleadoRequest r) {
        Empleado e = new Empleado();
        aplicar(e, r);
        return EmpleadoResponse.from(repository.save(e));
    }

    @Transactional
    public List<EmpleadoResponse> crearLote(List<EmpleadoRequest> lote) {
        List<Empleado> nuevos = lote.stream().map(r -> {
            Empleado e = new Empleado();
            aplicar(e, r);
            return e;
        }).toList();
        return repository.saveAll(nuevos).stream().map(EmpleadoResponse::from).toList();
    }

    @Transactional
    public EmpleadoResponse actualizar(Long id, EmpleadoRequest r) {
        Empleado e = obtenerEntidad(id);
        aplicar(e, r);
        return EmpleadoResponse.from(repository.save(e));
    }

    @Transactional
    public EmpleadoResponse cambiarActivo(Long id, boolean activo) {
        Empleado e = obtenerEntidad(id);
        e.setActivo(activo);
        return EmpleadoResponse.from(repository.save(e));
    }

    private void aplicar(Empleado e, EmpleadoRequest r) {
        e.setNombre(r.nombre().trim());
        e.setRol(r.rol());
        e.setCostoHora(r.costoHora());
        e.setHorasMaxSemana(r.horasMaxSemana() == null ? 48 : r.horasMaxSemana());
        if (r.activo() != null) {
            e.setActivo(r.activo());
        }
    }
}
