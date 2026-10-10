package mx.Tienda_inteligente.pls.plantilla;

import mx.Tienda_inteligente.pls.common.ConflictoException;
import mx.Tienda_inteligente.pls.common.Dinero;
import mx.Tienda_inteligente.pls.common.RecursoNoEncontradoException;
import mx.Tienda_inteligente.pls.common.ReglaNegocioException;
import mx.Tienda_inteligente.pls.configuracion.Configuracion;
import mx.Tienda_inteligente.pls.configuracion.ConfiguracionService;
import mx.Tienda_inteligente.pls.configuracion.Tamano;
import mx.Tienda_inteligente.pls.empleado.Empleado;
import mx.Tienda_inteligente.pls.empleado.EmpleadoRepository;
import mx.Tienda_inteligente.pls.empleado.Rol;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Service
public class PlantillaService {

    private final PlantillaRepository repository;
    private final EmpleadoRepository empleadoRepository;
    private final ConfiguracionService configuracionService;
    private final NamedParameterJdbcTemplate jdbc;

    public PlantillaService(PlantillaRepository repository, EmpleadoRepository empleadoRepository,
                            ConfiguracionService configuracionService, NamedParameterJdbcTemplate jdbc) {
        this.repository = repository;
        this.empleadoRepository = empleadoRepository;
        this.configuracionService = configuracionService;
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public List<PlantillaResponse> listar(Integer diaSemana, Rol rol, OrigenPlantilla origen) {
        return repository.findAllByOrderByDiaSemanaAscHoraInicioAsc().stream()
                .filter(p -> diaSemana == null || p.getDiaSemana() == diaSemana.shortValue())
                .filter(p -> rol == null || p.getRol() == rol)
                .filter(p -> origen == null || p.getOrigen() == origen)
                .map(PlantillaResponse::from)
                .toList();
    }

    /** Agrupado por dia (1-7), listo para pintar un calendario. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> semana() {
        Map<Integer, List<PlantillaResponse>> porDia = listar(null, null, null).stream()
                .collect(Collectors.groupingBy(PlantillaResponse::diaSemana, TreeMap::new, Collectors.toList()));
        List<Map<String, Object>> r = new ArrayList<>();
        for (int d = 1; d <= 7; d++) {
            List<PlantillaResponse> bloques = porDia.getOrDefault(d, List.of());
            Map<String, Object> dia = new LinkedHashMap<>();
            dia.put("diaSemana", d);
            dia.put("dia", PlantillaResponse.DIAS[d]);
            dia.put("horasPersona", Dinero.r2(bloques.stream().mapToDouble(PlantillaResponse::horasPersona).sum()));
            dia.put("bloques", bloques);
            r.add(dia);
        }
        return r;
    }

    @Transactional
    public PlantillaResponse crear(PlantillaRequest r) {
        PlantillaEmpleado p = new PlantillaEmpleado();
        p.setOrigen(OrigenPlantilla.MANUAL);
        aplicar(p, r);
        validar(p);
        return PlantillaResponse.from(repository.save(p));
    }

    @Transactional
    public PlantillaResponse actualizar(Long id, PlantillaRequest r) {
        PlantillaEmpleado p = repository.findById(id).orElseThrow(() -> RecursoNoEncontradoException.de("Plantilla", id));
        aplicar(p, r);
        validar(p);
        return PlantillaResponse.from(repository.save(p));
    }

    @Transactional
    public void borrar(Long id) {
        if (!repository.existsById(id)) {
            throw RecursoNoEncontradoException.de("Plantilla", id);
        }
        repository.deleteById(id);
    }

    private void aplicar(PlantillaEmpleado p, PlantillaRequest r) {
        p.setDiaSemana(r.diaSemana().shortValue());
        p.setHoraInicio(r.horaInicio());
        p.setHoraFin(r.horaFin());
        p.setRol(r.rol());
        p.setPersonalRequerido(r.personalRequerido());
    }

    private void validar(PlantillaEmpleado p) {
        if (!p.getHoraInicio().isBefore(p.getHoraFin())) {
            throw new ReglaNegocioException("horaInicio debe ser antes de horaFin", Map.of("horaInicio", "Menor que horaFin"));
        }
        Configuracion conf = configuracionService.obtenerEntidad();
        if (p.getHoraInicio().isBefore(conf.getHoraApertura()) || p.getHoraFin().isAfter(conf.getHoraCierre())) {
            throw new ReglaNegocioException("El bloque debe estar dentro del horario de la sucursal ("
                    + conf.getHoraApertura() + " - " + conf.getHoraCierre() + ")");
        }
        boolean traslape = repository.findAll().stream()
                .filter(o -> !o.getId().equals(p.getId()))
                .anyMatch(o -> o.getDiaSemana().equals(p.getDiaSemana()) && o.getRol() == p.getRol()
                        && o.getHoraInicio().isBefore(p.getHoraFin()) && p.getHoraInicio().isBefore(o.getHoraFin()));
        if (traslape) {
            throw new ConflictoException("Ya existe un bloque para ese dia y rol que se traslapa con este horario");
        }
    }

    // ------------------------------------------------------------------ recomendacion

    /**
     * Cuenta los tickets distintos por dia de la semana y hora en las ultimas N semanas,
     * divide entre transaccionesPorEmpleadoHora y redondea hacia arriba = cajeros necesarios.
     * PISO = la mitad de los cajeros (min 1); ALMACEN = recepcion en las primeras horas; GERENTE = 1 siempre.
     * Las horas consecutivas con el mismo requerimiento se juntan en un solo bloque.
     */
    @Transactional
    public RecomendacionPersonal recomendar(int semanas, boolean guardar) {
        if (semanas < 1 || semanas > 52) {
            throw new ReglaNegocioException("semanas debe estar entre 1 y 52");
        }
        Configuracion conf = configuracionService.obtenerEntidad();
        int tpe = conf.getTransaccionesPorEmpleadoHora();
        LocalDate hasta = LocalDate.now();
        LocalDate desde = hasta.minusWeeks(semanas);
        List<String> notas = new ArrayList<>();

        // tickets promedio por [dia][hora]
        double[][] tickets = new double[8][24];
        jdbc.query("""
                SELECT CAST(EXTRACT(ISODOW FROM fecha_hora) AS INT) d, CAST(EXTRACT(HOUR FROM fecha_hora) AS INT) h,
                       COUNT(DISTINCT ticket) tk
                FROM ventas WHERE fecha_hora >= :desde AND fecha_hora < :hasta
                GROUP BY 1, 2
                """, new MapSqlParameterSource().addValue("desde", desde.atStartOfDay()).addValue("hasta", hasta.atStartOfDay()),
                rs -> {
                    tickets[rs.getInt("d")][rs.getInt("h")] = rs.getDouble("tk") / semanas;
                });
        double total = 0;
        for (double[] fila : tickets) for (double v : fila) total += v;
        if (total == 0) {
            notas.add("No hay ventas en las ultimas " + semanas + " semanas: se recomiendan los minimos por rol");
        }

        int primeraHora = conf.getHoraApertura().getHour();
        int ultimaHora = conf.getHoraCierre().minusMinutes(1).getHour();
        int horasAlmacen = conf.getTamano() == Tamano.GRANDE ? 6 : 4;
        int almacenistas = conf.getTamano() == Tamano.GRANDE ? 2 : 1;

        // requerido[rol][dia][hora]
        Map<Rol, int[][]> requerido = new EnumMap<>(Rol.class);
        for (Rol rol : Rol.values()) requerido.put(rol, new int[8][24]);
        Map<Integer, Map<String, Double>> demanda = new TreeMap<>();
        for (int d = 1; d <= 7; d++) {
            Map<String, Double> porHora = new LinkedHashMap<>();
            for (int h = primeraHora; h <= ultimaHora; h++) {
                double tx = tickets[d][h];
                porHora.put(String.format("%02d", h), Dinero.r2(tx));
                int cajeros = Math.max(1, (int) Math.ceil(tx / tpe));
                requerido.get(Rol.CAJERO)[d][h] = cajeros;
                requerido.get(Rol.PISO)[d][h] = Math.max(1, (int) Math.ceil(cajeros * 0.5));
                requerido.get(Rol.ALMACEN)[d][h] = (h - primeraHora) < horasAlmacen ? almacenistas : 0;
                requerido.get(Rol.GERENTE)[d][h] = 1;
            }
            demanda.put(d, porHora);
        }

        // Juntar horas consecutivas con el mismo numero en bloques
        List<PlantillaEmpleado> bloques = new ArrayList<>();
        for (Rol rol : Rol.values()) {
            int[][] req = requerido.get(rol);
            for (int d = 1; d <= 7; d++) {
                int h = primeraHora;
                while (h <= ultimaHora) {
                    int n = req[d][h];
                    int fin = h;
                    while (fin + 1 <= ultimaHora && req[d][fin + 1] == n) fin++;
                    if (n > 0) {
                        PlantillaEmpleado b = new PlantillaEmpleado();
                        b.setDiaSemana((short) d);
                        b.setRol(rol);
                        b.setPersonalRequerido(n);
                        b.setOrigen(OrigenPlantilla.RECOMENDADO);
                        b.setHoraInicio(max(conf.getHoraApertura(), LocalTime.of(h, 0)));
                        b.setHoraFin(fin + 1 >= 24 ? conf.getHoraCierre() : min(conf.getHoraCierre(), LocalTime.of(fin + 1, 0)));
                        bloques.add(b);
                    }
                    h = fin + 1;
                }
            }
        }

        if (guardar) {
            repository.borrarPorOrigen(OrigenPlantilla.RECOMENDADO);
            List<PlantillaEmpleado> manuales = repository.findByOrigen(OrigenPlantilla.MANUAL);
            int antes = bloques.size();
            bloques.removeIf(b -> manuales.stream().anyMatch(m -> m.getDiaSemana().equals(b.getDiaSemana())
                    && m.getRol() == b.getRol()
                    && m.getHoraInicio().isBefore(b.getHoraFin()) && b.getHoraInicio().isBefore(m.getHoraFin())));
            if (bloques.size() < antes) {
                notas.add((antes - bloques.size()) + " bloques recomendados se omitieron porque ya hay bloques MANUALES en ese horario");
            }
            bloques = repository.saveAll(bloques);
        }

        // Costo semanal: horas-persona x costo promedio por hora del rol
        Map<Rol, Double> costoRol = costoPromedioPorRol();
        Map<String, Double> horasPorRol = new LinkedHashMap<>();
        double costo = 0;
        for (PlantillaEmpleado b : bloques) {
            double hp = b.horas() * b.getPersonalRequerido();
            horasPorRol.merge(b.getRol().name(), hp, Double::sum);
            costo += hp * costoRol.getOrDefault(b.getRol(), 0.0);
        }
        horasPorRol.replaceAll((k, v) -> Dinero.r2(v));

        List<PlantillaResponse> respuesta = bloques.stream()
                .sorted(Comparator.comparing(PlantillaEmpleado::getDiaSemana)
                        .thenComparing(PlantillaEmpleado::getRol)
                        .thenComparing(PlantillaEmpleado::getHoraInicio))
                .map(PlantillaResponse::from).toList();
        return new RecomendacionPersonal(semanas, tpe, guardar, respuesta, horasPorRol, Dinero.de(costo), demanda, notas);
    }

    /** Personal requerido (plantillas guardadas) vs empleados activos, con el deficit por rol. */
    @Transactional(readOnly = true)
    public List<CoberturaRol> cobertura() {
        List<PlantillaEmpleado> plantillas = repository.findAll();
        List<Empleado> activos = empleadoRepository.findByActivoTrue();
        List<CoberturaRol> r = new ArrayList<>();
        for (Rol rol : Rol.values()) {
            double requeridas = 0;
            int pico = 0;
            for (PlantillaEmpleado p : plantillas) {
                if (p.getRol() != rol) continue;
                requeridas += p.horas() * p.getPersonalRequerido();
                pico = Math.max(pico, p.getPersonalRequerido());
            }
            List<Empleado> delRol = activos.stream().filter(e -> e.getRol() == rol).toList();
            double disponibles = delRol.stream().mapToInt(Empleado::getHorasMaxSemana).sum();
            double deficit = requeridas - disponibles;
            double horasPorEmpleado = delRol.isEmpty() ? 48 : disponibles / delRol.size();
            int faltantes = Math.max(Math.max(0, pico - delRol.size()),
                    deficit > 0 ? (int) Math.ceil(deficit / horasPorEmpleado) : 0);
            String estado;
            if (requeridas == 0 && delRol.isEmpty()) estado = "SIN_DATOS";
            else if (faltantes > 0) estado = "DEFICIT";
            else if (disponibles < requeridas * 1.1) estado = "JUSTO";
            else if (requeridas > 0 && disponibles > requeridas * 1.5) estado = "EXCESO";
            else estado = "OK";
            r.add(new CoberturaRol(rol, Dinero.r2(requeridas), Dinero.r2(disponibles), Dinero.r2(deficit), pico,
                    delRol.size(), faltantes, estado));
        }
        return r;
    }

    private Map<Rol, Double> costoPromedioPorRol() {
        List<Empleado> activos = empleadoRepository.findByActivoTrue();
        double general = activos.stream().map(Empleado::getCostoHora).mapToDouble(BigDecimal::doubleValue).average().orElse(0);
        Map<Rol, Double> r = new EnumMap<>(Rol.class);
        for (Rol rol : Rol.values()) {
            r.put(rol, activos.stream().filter(e -> e.getRol() == rol).map(Empleado::getCostoHora)
                    .mapToDouble(BigDecimal::doubleValue).average().orElse(general));
        }
        return r;
    }

    private static LocalTime max(LocalTime a, LocalTime b) {
        return a.isAfter(b) ? a : b;
    }

    private static LocalTime min(LocalTime a, LocalTime b) {
        return a.isBefore(b) ? a : b;
    }
}
