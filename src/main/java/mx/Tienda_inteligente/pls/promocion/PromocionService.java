package mx.Tienda_inteligente.pls.promocion;

import mx.Tienda_inteligente.pls.analitica.EstadisticasVentas;
import mx.Tienda_inteligente.pls.analitica.SerieDiaria;
import mx.Tienda_inteligente.pls.common.ConflictoException;
import mx.Tienda_inteligente.pls.common.Dinero;
import mx.Tienda_inteligente.pls.common.RecursoNoEncontradoException;
import mx.Tienda_inteligente.pls.common.ReglaNegocioException;
import mx.Tienda_inteligente.pls.configuracion.Configuracion;
import mx.Tienda_inteligente.pls.configuracion.ConfiguracionService;
import mx.Tienda_inteligente.pls.ia.ClienteIa;
import mx.Tienda_inteligente.pls.producto.Producto;
import mx.Tienda_inteligente.pls.producto.ProductoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class PromocionService {

    private static final Logger log = LoggerFactory.getLogger(PromocionService.class);

    private final PromocionRepository repository;
    private final ProductoRepository productoRepository;
    private final ConfiguracionService configuracionService;
    private final MotorPromociones motor;
    private final ClienteIa clienteIa;
    private final EstadisticasVentas estadisticas;
    private final NamedParameterJdbcTemplate jdbc;

    public PromocionService(PromocionRepository repository, ProductoRepository productoRepository,
                            ConfiguracionService configuracionService, MotorPromociones motor, ClienteIa clienteIa,
                            EstadisticasVentas estadisticas, NamedParameterJdbcTemplate jdbc) {
        this.repository = repository;
        this.productoRepository = productoRepository;
        this.configuracionService = configuracionService;
        this.motor = motor;
        this.clienteIa = clienteIa;
        this.estadisticas = estadisticas;
        this.jdbc = jdbc;
    }

    // ------------------------------------------------------------------ consultas

    @Transactional(readOnly = true)
    public List<PromocionResponse> listar(EstadoPromocion estado, OrigenPromocion origen, MotivoPromocion motivo,
                                          Long productoId, Boolean vigentes) {
        LocalDate hoy = LocalDate.now();
        List<Promocion> promos = repository.findAll().stream()
                .filter(p -> estado == null || p.getEstado() == estado)
                .filter(p -> origen == null || p.getOrigen() == origen)
                .filter(p -> motivo == null || p.getMotivo() == motivo)
                .filter(p -> productoId == null || p.getProductoId().equals(productoId))
                .filter(p -> vigentes == null || p.vigenteEn(hoy) == vigentes)
                .sorted(Comparator.comparing(Promocion::getId).reversed())
                .toList();
        return responder(promos);
    }

    @Transactional(readOnly = true)
    public PromocionResponse obtener(Long id) {
        return responder(List.of(obtenerEntidad(id))).get(0);
    }

    private Promocion obtenerEntidad(Long id) {
        return repository.findById(id).orElseThrow(() -> RecursoNoEncontradoException.de("Promocion", id));
    }

    // ------------------------------------------------------------------ escritura manual

    @Transactional
    public PromocionResponse crear(PromocionRequest r) {
        Producto producto = producto(r.productoId());
        Promocion p = new Promocion();
        p.setProductoId(producto.getId());
        p.setOrigen(OrigenPromocion.MANUAL);
        aplicar(p, r);
        validar(p, producto, configuracionService.obtenerEntidad());
        if (Boolean.TRUE.equals(r.activar())) {
            validarSinTraslape(p);
            p.setEstado(EstadoPromocion.ACTIVA);
        } else {
            p.setEstado(EstadoPromocion.SUGERIDA);
        }
        return responder(List.of(repository.save(p))).get(0);
    }

    /** Solo se puede editar mientras esta SUGERIDA. */
    @Transactional
    public PromocionResponse actualizar(Long id, PromocionRequest r) {
        Promocion p = obtenerEntidad(id);
        if (p.getEstado() != EstadoPromocion.SUGERIDA) {
            throw new ConflictoException("Solo se pueden editar promociones SUGERIDAS (esta es " + p.getEstado() + ")");
        }
        Producto producto = producto(r.productoId());
        p.setProductoId(producto.getId());
        aplicar(p, r);
        validar(p, producto, configuracionService.obtenerEntidad());
        return responder(List.of(repository.save(p))).get(0);
    }

    /** Maquina de estados: SUGERIDA -> ACTIVA | RECHAZADA, ACTIVA -> FINALIZADA. */
    @Transactional
    public PromocionResponse cambiarEstado(Long id, EstadoPromocion nuevo) {
        Promocion p = obtenerEntidad(id);
        EstadoPromocion actual = p.getEstado();
        boolean valida = (actual == EstadoPromocion.SUGERIDA
                && (nuevo == EstadoPromocion.ACTIVA || nuevo == EstadoPromocion.RECHAZADA))
                || (actual == EstadoPromocion.ACTIVA && nuevo == EstadoPromocion.FINALIZADA);
        if (!valida) {
            throw new ConflictoException("Transicion invalida: " + actual + " -> " + nuevo);
        }
        if (nuevo == EstadoPromocion.ACTIVA) {
            Producto producto = producto(p.getProductoId());
            validar(p, producto, configuracionService.obtenerEntidad());
            validarSinTraslape(p);
        }
        if (nuevo == EstadoPromocion.FINALIZADA) {
            p.setVentasReales(ventasReales(p.getId()));
            if (p.getFechaFin().isAfter(LocalDate.now())) {
                p.setFechaFin(LocalDate.now().isBefore(p.getFechaInicio()) ? p.getFechaInicio() : LocalDate.now());
            }
        }
        p.setEstado(nuevo);
        return responder(List.of(repository.save(p))).get(0);
    }

    /** Finaliza las promociones ACTIVAS cuya fecha fin ya paso y guarda sus ventas reales. */
    @Transactional
    public int finalizarVencidas() {
        int n = 0;
        for (Promocion p : repository.findByEstado(EstadoPromocion.ACTIVA)) {
            if (p.getFechaFin().isBefore(LocalDate.now())) {
                p.setVentasReales(ventasReales(p.getId()));
                p.setEstado(EstadoPromocion.FINALIZADA);
                n++;
            }
        }
        return n;
    }

    private void aplicar(Promocion p, PromocionRequest r) {
        p.setTipo(r.tipo());
        p.setValor(r.tipo() == TipoPromocion.DOS_X_UNO ? BigDecimal.ZERO
                : (r.valor() == null ? BigDecimal.ZERO : r.valor()));
        p.setFechaInicio(r.fechaInicio());
        p.setFechaFin(r.fechaFin());
        p.setMotivo(r.motivo());
        p.setExplicacion(r.explicacion());
        p.setVentasEsperadas(r.ventasEsperadas());
        p.setGananciaEsperada(r.gananciaEsperada());
    }

    /** Reglas que tambien aplica el motor: margen minimo, descuento maximo y valores coherentes. */
    void validar(Promocion p, Producto producto, Configuracion conf) {
        if (p.getFechaInicio().isAfter(p.getFechaFin())) {
            throw new ReglaNegocioException("fechaInicio debe ser anterior o igual a fechaFin",
                    Map.of("fechaFin", "Debe ser >= fechaInicio"));
        }
        BigDecimal precio = producto.getPrecioVenta();
        switch (p.getTipo()) {
            case PORCENTAJE -> {
                if (p.getValor().signum() <= 0 || p.getValor().compareTo(BigDecimal.valueOf(100)) >= 0) {
                    throw new ReglaNegocioException("El porcentaje debe estar entre 0 y 100",
                            Map.of("valor", "Entre 0 y 100 (exclusivo)"));
                }
            }
            case PRECIO_FIJO -> {
                if (p.getValor().signum() <= 0 || p.getValor().compareTo(precio) >= 0) {
                    throw new ReglaNegocioException("El precio fijo debe ser menor al precio normal (" + precio + ")",
                            Map.of("valor", "Menor que " + precio));
                }
            }
            case DOS_X_UNO -> {
                // el valor se ignora
            }
        }
        BigDecimal descuento = CalculadoraPromocion.porcentajeDescuento(p.getTipo(), p.getValor(), precio);
        if (descuento.compareTo(conf.getDescuentoMaximo()) > 0) {
            throw new ReglaNegocioException("El descuento (" + descuento + "%) supera el maximo permitido",
                    Map.of("valor", "Maximo permitido: " + conf.getDescuentoMaximo().stripTrailingZeros().toPlainString() + "%"));
        }
        BigDecimal efectivo = CalculadoraPromocion.precioEfectivoUnitario(p.getTipo(), p.getValor(), precio);
        BigDecimal minimo = CalculadoraPromocion.precioMinimo(producto.getCosto(), conf.getMargenMinimoPromo());
        if (efectivo.compareTo(minimo) < 0) {
            BigDecimal maxPct = BigDecimal.ONE.subtract(minimo.divide(precio, 6, RoundingMode.HALF_UP))
                    .multiply(BigDecimal.valueOf(100)).setScale(1, RoundingMode.DOWN);
            throw new ReglaNegocioException("El precio con descuento queda por debajo del margen minimo",
                    Map.of("valor", "Maximo permitido por margen: " + maxPct + "%"));
        }
    }

    private void validarSinTraslape(Promocion p) {
        List<Promocion> otras = new ArrayList<>(repository.traslapadas(p.getProductoId(), EnumSet.of(EstadoPromocion.ACTIVA),
                p.getFechaInicio(), p.getFechaFin()));
        otras.removeIf(o -> o.getId() != null && o.getId().equals(p.getId()));
        if (!otras.isEmpty()) {
            throw new ConflictoException("Ya hay una promocion ACTIVA para este producto en esas fechas (id "
                    + otras.get(0).getId() + ")");
        }
    }

    private Producto producto(Long id) {
        return productoRepository.findById(id).orElseThrow(() -> RecursoNoEncontradoException.de("Producto", id));
    }

    // ------------------------------------------------------------------ motor

    /**
     * Ejecuta el motor y guarda las sugerencias como SUGERIDA.
     * Con IA activa, la IA revisa las mejores candidatas (descuento, duracion y explicacion);
     * su propuesta se vuelve a validar con las mismas reglas. Si la IA falla, quedan las del algoritmo.
     */
    @Transactional
    public List<PromocionResponse> generar(GenerarPromocionesRequest req) {
        int max = req.maxSugerencias() == null ? 10 : req.maxSugerencias();
        int horizonte = req.horizonteDias() == null ? 14 : req.horizonteDias();
        String modo = req.modelo() == null ? "AUTO" : req.modelo().toUpperCase(Locale.ROOT);
        boolean usarIa = switch (modo) {
            case "AUTO" -> configuracionService.iaActiva();
            case "BASE", "ALGORITMO" -> false;
            case "IA" -> {
                if (!clienteIa.disponible()) {
                    throw new ReglaNegocioException("La IA no esta configurada en el servidor (falta IA_API_KEY)");
                }
                yield true;
            }
            default -> throw new ReglaNegocioException("modelo invalido: " + modo, Map.of("modelo", "AUTO, BASE o IA"));
        };

        finalizarVencidas();
        if (req.reemplazar() == null || req.reemplazar()) {
            repository.borrarSugerenciasAutomaticas();
        }
        Configuracion conf = configuracionService.obtenerEntidad();
        List<MotorPromociones.Sugerencia> candidatas = motor.sugerir(conf, horizonte).stream()
                .filter(s -> repository.traslapadas(s.producto().productoId(),
                        EnumSet.of(EstadoPromocion.ACTIVA, EstadoPromocion.SUGERIDA),
                        s.fechaInicio(), s.fechaFin()).isEmpty())
                .limit(max)
                .toList();

        Map<Long, Producto> productos = productoRepository.findAllById(
                        candidatas.stream().map(s -> s.producto().productoId()).toList())
                .stream().collect(Collectors.toMap(Producto::getId, Function.identity()));

        Map<Long, AjusteIa> ajustes = usarIa ? revisarConIa(candidatas, conf) : Map.of();

        List<Promocion> guardar = new ArrayList<>();
        for (MotorPromociones.Sugerencia s : candidatas) {
            OrigenPromocion origen = OrigenPromocion.ALGORITMO;
            AjusteIa ajuste = ajustes.get(s.producto().productoId());
            if (ajuste != null) {
                if (!ajuste.incluir()) continue;
                MotorPromociones.Sugerencia rev = motor.evaluar(s.producto(), s.motivo(), s.fechaInicio(),
                        s.fechaInicio().plusDays(Math.max(1, ajuste.dias()) - 1L), s.factorTemporada(), s.evento(),
                        s.elasticidad(), ajuste.descuento(), s.motivo() == MotivoPromocion.POR_CADUCAR);
                if (rev.gananciaIncremental() > 0) {
                    s = new MotorPromociones.Sugerencia(rev.producto(), rev.motivo(), rev.descuento(), rev.fechaInicio(),
                            rev.fechaFin(), rev.ventasEsperadas(), rev.ventasSinPromo(), rev.gananciaIncremental(),
                            rev.perdidaEvitada(), rev.elasticidad(), rev.factorTemporada(), rev.evento(),
                            ajuste.explicacion() == null || ajuste.explicacion().isBlank() ? rev.explicacion() : ajuste.explicacion());
                    origen = OrigenPromocion.IA;
                }
            }
            Promocion p = new Promocion();
            p.setProductoId(s.producto().productoId());
            p.setTipo(TipoPromocion.PORCENTAJE);
            p.setValor(MotorPromociones.porcentaje(s.descuento()));
            p.setFechaInicio(s.fechaInicio());
            p.setFechaFin(s.fechaFin());
            p.setEstado(EstadoPromocion.SUGERIDA);
            p.setOrigen(origen);
            p.setMotivo(s.motivo());
            p.setVentasEsperadas(s.ventasEsperadas());
            p.setGananciaEsperada(Dinero.de(s.gananciaIncremental()));
            p.setExplicacion(s.explicacion());
            try {
                validar(p, productos.get(p.getProductoId()), conf);
                guardar.add(p);
            } catch (ReglaNegocioException e) {
                log.debug("Sugerencia descartada para producto {}: {}", p.getProductoId(), e.getMessage());
            }
        }
        return responder(repository.saveAll(guardar));
    }

    private record AjusteIa(boolean incluir, double descuento, int dias, String explicacion) {
    }

    private Map<Long, AjusteIa> revisarConIa(List<MotorPromociones.Sugerencia> candidatas, Configuracion conf) {
        if (candidatas.isEmpty()) return Map.of();
        String sistema = """
                Eres el gerente comercial de una tienda de retail en Mexico. Un algoritmo propone promociones.
                Revisa cada una y decide si conviene, ajustando descuento (porcentaje entero) y duracion en dias.
                Reglas duras: descuento entre 5 y %s; nunca vender por debajo de costo x (1 + %s).
                Prioriza evitar mermas por caducidad y mover sobrestock sin regalar margen.
                Escribe una explicacion breve (1-2 frases, en espanol, con numeros) para el gerente.
                Responde UNICAMENTE con un arreglo JSON:
                [{"productoId": 1, "incluir": true, "descuento": 20, "dias": 3, "explicacion": "..."}]
                """.formatted(conf.getDescuentoMaximo().stripTrailingZeros().toPlainString(),
                conf.getMargenMinimoPromo().toPlainString());
        StringBuilder prompt = new StringBuilder("PROPUESTAS DEL ALGORITMO:\n");
        for (MotorPromociones.Sugerencia s : candidatas) {
            var e = s.producto();
            prompt.append(String.format(Locale.ROOT,
                    "- productoId=%d | %s | categoria=%s | precio=%s | costo=%s | stock=%d | venta diaria=%.2f | "
                            + "cobertura=%s dias | motivo=%s | descuento propuesto=%d%% | dias=%d | ventas esperadas=%d | "
                            + "ganancia incremental=$%.2f | %s%n",
                    e.productoId(), e.nombre(), e.categoria(), e.precioVenta(), e.costoUnitario(), e.stockActual(),
                    e.demandaDiaria(), e.diasCobertura() == null ? "sin ventas" : e.diasCobertura(), s.motivo(),
                    Math.round(s.descuento() * 100), s.dias(), s.ventasEsperadas(), s.gananciaIncremental(),
                    s.explicacion()));
        }
        Optional<List<Object>> json = clienteIa.completarJsonArreglo(sistema, prompt.toString(), 3000);
        Map<Long, AjusteIa> r = new HashMap<>();
        json.ifPresent(lista -> {
            for (Object o : lista) {
                if (!(o instanceof Map<?, ?> m)) continue;
                Double pid = ClienteIa.numero(m.get("productoId"));
                Double desc = ClienteIa.numero(m.get("descuento"));
                Double dias = ClienteIa.numero(m.get("dias"));
                if (pid == null || desc == null) continue;
                boolean incluir = !Boolean.FALSE.equals(m.get("incluir"));
                double d = Math.max(0.05, Math.min(conf.getDescuentoMaximo().doubleValue(), desc) / 100.0);
                r.put(pid.longValue(), new AjusteIa(incluir, d, dias == null ? 7 : Math.min(30, dias.intValue()),
                        m.get("explicacion") == null ? null : m.get("explicacion").toString()));
            }
        });
        if (json.isEmpty()) {
            log.warn("La IA no respondio; se guardan las sugerencias del algoritmo");
        }
        return r;
    }

    // ------------------------------------------------------------------ resultado / ROI

    @Transactional(readOnly = true)
    public ResultadoPromocion resultado(Long id) {
        Promocion p = obtenerEntidad(id);
        Producto producto = producto(p.getProductoId());
        LocalDate hoy = LocalDate.now();
        LocalDate fin = p.getFechaFin().isAfter(hoy) ? hoy : p.getFechaFin();
        long dias = p.getFechaInicio().isAfter(hoy) ? 0 : ChronoUnit.DAYS.between(p.getFechaInicio(), fin) + 1;

        Map<String, Object> ventas = jdbc.queryForMap("""
                SELECT COALESCE(SUM(cantidad), 0) uds,
                       COALESCE(SUM(cantidad * precio_unitario - descuento_aplicado), 0) ingresos,
                       COALESCE(SUM(descuento_aplicado), 0) descuento
                FROM ventas WHERE promocion_id = :id
                """, new MapSqlParameterSource("id", id));
        int reales = ((Number) ventas.get("uds")).intValue();
        BigDecimal ingresos = Dinero.redondear(new BigDecimal(ventas.get("ingresos").toString()));
        BigDecimal descuento = Dinero.redondear(new BigDecimal(ventas.get("descuento").toString()));
        BigDecimal margenReal = ingresos.subtract(producto.getCosto().multiply(BigDecimal.valueOf(reales)));

        // Linea base: promedio diario de las 4 semanas previas a la promo
        LocalDate baseDesde = p.getFechaInicio().minusDays(28);
        SerieDiaria previa = estadisticas.series(baseDesde, p.getFechaInicio().minusDays(1), List.of(p.getProductoId()))
                .get(p.getProductoId());
        double mediaPrevia = previa == null ? 0 : previa.promedio();
        double sinPromo = mediaPrevia * dias;
        BigDecimal margenSinPromo = Dinero.de(sinPromo * producto.getPrecioVenta().subtract(producto.getCosto()).doubleValue());
        BigDecimal incremental = margenReal.subtract(margenSinPromo);
        double incremento = sinPromo > 0 ? Dinero.r2(100 * (reales - sinPromo) / sinPromo) : 0;
        Double roi = descuento.signum() > 0 ? Dinero.r2(incremental.doubleValue() / descuento.doubleValue()) : null;
        Double cumplimiento = p.getVentasEsperadas() != null && p.getVentasEsperadas() > 0
                ? Dinero.r2(100.0 * reales / p.getVentasEsperadas()) : null;

        return new ResultadoPromocion(p.getId(), p.getProductoId(), p.getEstado(), p.getFechaInicio(), p.getFechaFin(),
                dias, p.getVentasEsperadas(), reales, Dinero.r2(sinPromo), incremento, ingresos, descuento, margenReal,
                margenSinPromo, incremental, p.getGananciaEsperada(), roi, cumplimiento);
    }

    private int ventasReales(Long promocionId) {
        Integer n = jdbc.queryForObject("SELECT COALESCE(SUM(cantidad), 0) FROM ventas WHERE promocion_id = :id",
                new MapSqlParameterSource("id", promocionId), Integer.class);
        return n == null ? 0 : n;
    }

    // ------------------------------------------------------------------ mapeo

    private List<PromocionResponse> responder(List<Promocion> promos) {
        Map<Long, Producto> productos = productoRepository.findAllById(
                        promos.stream().map(Promocion::getProductoId).distinct().toList())
                .stream().collect(Collectors.toMap(Producto::getId, Function.identity()));
        return promos.stream().map(p -> {
            Producto pr = productos.get(p.getProductoId());
            BigDecimal precio = pr == null ? null : pr.getPrecioVenta();
            BigDecimal efectivo = pr == null ? null
                    : Dinero.redondear(CalculadoraPromocion.precioEfectivoUnitario(p.getTipo(), p.getValor(), precio));
            BigDecimal pct = pr == null ? null : CalculadoraPromocion.porcentajeDescuento(p.getTipo(), p.getValor(), precio);
            return new PromocionResponse(p.getId(), p.getProductoId(), pr == null ? null : pr.getSku(),
                    pr == null ? null : pr.getNombre(), p.getTipo(), p.getValor(), p.getFechaInicio(), p.getFechaFin(),
                    p.getEstado(), p.getOrigen(), p.getMotivo(), p.getVentasEsperadas(), p.getVentasReales(),
                    p.getGananciaEsperada(), precio, efectivo, pct, p.getExplicacion(), p.getCreadaEn());
        }).toList();
    }
}
