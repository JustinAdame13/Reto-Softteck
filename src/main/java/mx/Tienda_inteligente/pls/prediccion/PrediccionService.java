package mx.Tienda_inteligente.pls.prediccion;

import mx.Tienda_inteligente.pls.analitica.EstadisticasVentas;
import mx.Tienda_inteligente.pls.analitica.SerieDiaria;
import mx.Tienda_inteligente.pls.common.Dinero;
import mx.Tienda_inteligente.pls.common.ReglaNegocioException;
import mx.Tienda_inteligente.pls.configuracion.Configuracion;
import mx.Tienda_inteligente.pls.configuracion.ConfiguracionService;
import mx.Tienda_inteligente.pls.contexto.ContextoDto;
import mx.Tienda_inteligente.pls.contexto.ContextoService;
import mx.Tienda_inteligente.pls.inventario.AnalisisInventario;
import mx.Tienda_inteligente.pls.prediccion.motor.EntradaPrediccion;
import mx.Tienda_inteligente.pls.prediccion.motor.MotorPrediccionBase;
import mx.Tienda_inteligente.pls.prediccion.motor.MotorPrediccionExterno;
import mx.Tienda_inteligente.pls.prediccion.motor.MotorPrediccionIa;
import mx.Tienda_inteligente.pls.prediccion.motor.ProductoHistorial;
import mx.Tienda_inteligente.pls.prediccion.motor.ResultadoPrediccion;
import mx.Tienda_inteligente.pls.producto.Producto;
import mx.Tienda_inteligente.pls.producto.ProductoRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class PrediccionService {

    private final PrediccionRepository repository;
    private final ProductoRepository productoRepository;
    private final ConfiguracionService configuracionService;
    private final ContextoService contextoService;
    private final EstadisticasVentas estadisticas;
    private final MotorPrediccionBase motorBase;
    private final MotorPrediccionExterno motorExterno;
    private final MotorPrediccionIa motorIa;
    private final NamedParameterJdbcTemplate jdbc;

    public PrediccionService(PrediccionRepository repository, ProductoRepository productoRepository,
                             ConfiguracionService configuracionService, ContextoService contextoService,
                             EstadisticasVentas estadisticas, MotorPrediccionBase motorBase,
                             MotorPrediccionExterno motorExterno, MotorPrediccionIa motorIa,
                             NamedParameterJdbcTemplate jdbc) {
        this.repository = repository;
        this.productoRepository = productoRepository;
        this.configuracionService = configuracionService;
        this.contextoService = contextoService;
        this.estadisticas = estadisticas;
        this.motorBase = motorBase;
        this.motorExterno = motorExterno;
        this.motorIa = motorIa;
        this.jdbc = jdbc;
    }

    /** Ejecuta el motor y guarda (reemplaza) las predicciones. */
    @Transactional
    public GeneracionResponse generar(GenerarPrediccionRequest req) {
        int dias = req.dias() == null ? 7 : req.dias();
        LocalDate inicio = req.fechaInicio() != null ? req.fechaInicio() : LocalDate.now();
        LocalDate fin = inicio.plusDays(dias - 1L);
        String modo = req.modelo() == null ? "AUTO" : req.modelo().toUpperCase(Locale.ROOT);
        List<String> advertencias = new ArrayList<>();

        boolean usarIa = switch (modo) {
            case "AUTO" -> configuracionService.iaActiva();
            case "BASE" -> false;
            case "IA", "AMBOS" -> {
                if (!motorIa.disponible()) {
                    throw new ReglaNegocioException("La IA no esta configurada en el servidor (falta IA_API_KEY)",
                            Map.of("modelo", "Usa BASE o AUTO"));
                }
                yield true;
            }
            default -> throw new ReglaNegocioException("modelo invalido: " + modo,
                    Map.of("modelo", "Usa AUTO, BASE, IA o AMBOS"));
        };

        List<Producto> productos = (req.productoIds() == null || req.productoIds().isEmpty())
                ? productoRepository.findByActivoTrueOrderByNombre()
                : productoRepository.findAllById(req.productoIds());
        if (productos.isEmpty()) {
            throw new ReglaNegocioException("No hay productos para predecir");
        }

        EntradaPrediccion entrada = construirEntrada(productos, inicio, dias);

        // BASE: motor externo (Python) si esta configurado; si no o si falla, el interno en Java
        String motorUsado = "INTERNO";
        List<ResultadoPrediccion> base = null;
        if (motorExterno.habilitado()) {
            base = motorExterno.predecir(entrada).orElse(null);
            if (base == null) {
                advertencias.add("El motor externo no respondio; se uso el motor interno");
            } else {
                motorUsado = "EXTERNO";
            }
        }
        if (base == null) {
            base = motorBase.predecir(entrada);
        }

        Map<ModeloPrediccion, List<ResultadoPrediccion>> porModelo = new LinkedHashMap<>();
        porModelo.put(ModeloPrediccion.BASE, base);
        if (usarIa) {
            motorIa.potenciar(entrada, base).ifPresentOrElse(
                    ia -> porModelo.put(ModeloPrediccion.IA, ia),
                    () -> advertencias.add("La IA no respondio a tiempo; solo se guardo el modelo BASE"));
        }

        Set<Long> ids = productos.stream().map(Producto::getId).collect(Collectors.toSet());
        repository.borrar(ids, porModelo.keySet(), inicio, fin);

        Map<Long, Producto> porId = productos.stream().collect(Collectors.toMap(Producto::getId, Function.identity()));
        LocalDateTime ahora = LocalDateTime.now();
        List<PrediccionDemanda> guardar = new ArrayList<>();
        porModelo.forEach((modelo, resultados) -> {
            Map<Long, List<ResultadoPrediccion>> agrupados = resultados.stream()
                    .filter(r -> porId.containsKey(r.productoId()))
                    .filter(r -> !r.fechaObjetivo().isBefore(inicio) && !r.fechaObjetivo().isAfter(fin))
                    .collect(Collectors.groupingBy(ResultadoPrediccion::productoId));
            agrupados.forEach((pid, lista) -> {
                lista.sort(Comparator.comparing(ResultadoPrediccion::fechaObjetivo));
                guardar.addAll(planificar(porId.get(pid), lista, modelo, ahora));
            });
        });
        List<PrediccionResponse> respuesta = repository.saveAll(guardar).stream()
                .map(PrediccionResponse::from)
                .sorted(Comparator.comparing(PrediccionResponse::productoId)
                        .thenComparing(PrediccionResponse::fechaObjetivo)
                        .thenComparing(PrediccionResponse::modelo))
                .toList();

        return new GeneracionResponse(inicio, fin, new ArrayList<>(porModelo.keySet()), productos.size(),
                respuesta.size(), motorUsado, advertencias, respuesta);
    }

    /** Arma el contrato para el motor: historial diario de cada producto + calendario. */
    EntradaPrediccion construirEntrada(List<Producto> productos, LocalDate inicio, int dias) {
        Configuracion conf = configuracionService.obtenerEntidad();
        LocalDate histDesde = inicio.minusMonths(conf.getMesesHistorico());
        LocalDate histHasta = inicio.minusDays(1);
        Map<Long, SerieDiaria> series = estadisticas.series(histDesde, histHasta, null);

        List<ProductoHistorial> historiales = productos.stream().map(p -> {
            SerieDiaria s = series.getOrDefault(p.getId(), estadisticas.serieVacia(p.getId(), histDesde, histHasta));
            return new ProductoHistorial(p.getId(), p.getSku(), p.getNombre(), p.getCategoria(), p.getPrecioVenta(),
                    p.getCosto(), p.getStockActual(), p.getLeadTimeDias(), p.isEsPerecedero(), p.getVidaUtilDias(),
                    histDesde, s.valores());
        }).toList();

        List<ContextoDto> contexto = new ArrayList<>(
                new TreeMap<>(contextoService.mapa(histDesde, inicio.plusDays(dias - 1L))).values());
        return new EntradaPrediccion(inicio, dias, historiales, contexto);
    }

    /**
     * Convierte la demanda predicha en un plan de inventario:
     * stockRecomendado(dia) = demanda de los siguientes leadTime dias + stock de seguridad;
     * cantidadAPedir(dia)   = lo que falta para llegar a ese nivel segun el stock proyectado.
     */
    private List<PrediccionDemanda> planificar(Producto p, List<ResultadoPrediccion> lista, ModeloPrediccion modelo,
                                               LocalDateTime ahora) {
        int lead = Math.max(1, p.getLeadTimeDias());
        double promedio = lista.stream().mapToDouble(ResultadoPrediccion::cantidadPredicha).average().orElse(0);
        double stockProyectado = p.getStockActual();
        List<PrediccionDemanda> r = new ArrayList<>();
        for (int i = 0; i < lista.size(); i++) {
            ResultadoPrediccion res = lista.get(i);
            double demandaLead = 0;
            for (int j = i; j < i + lead; j++) {
                demandaLead += j < lista.size() ? lista.get(j).cantidadPredicha() : promedio;
            }
            double sigma = Math.max(0, res.limiteSuperior() - res.cantidadPredicha()) / 1.2816;
            int recomendado = (int) Math.ceil(demandaLead + AnalisisInventario.Z_NIVEL_SERVICIO * sigma * Math.sqrt(lead));
            if (p.getStockMaximo() != null) {
                recomendado = Math.min(recomendado, p.getStockMaximo());
            }
            int pedir = (int) Math.max(0, Math.ceil(recomendado - stockProyectado));
            stockProyectado = Math.max(0, stockProyectado + pedir - res.cantidadPredicha());

            PrediccionDemanda pd = new PrediccionDemanda();
            pd.setProductoId(p.getId());
            pd.setFechaObjetivo(res.fechaObjetivo());
            pd.setCantidadPredicha(Dinero.de(res.cantidadPredicha()));
            pd.setLimiteInferior(Dinero.de(Math.min(res.limiteInferior(), res.cantidadPredicha())));
            pd.setLimiteSuperior(Dinero.de(Math.max(res.limiteSuperior(), res.cantidadPredicha())));
            pd.setStockRecomendado(recomendado);
            pd.setCantidadAPedir(pedir);
            pd.setModelo(modelo);
            pd.setGeneradaEn(ahora);
            r.add(pd);
        }
        return r;
    }

    @Transactional(readOnly = true)
    public List<PrediccionResponse> listar(Long productoId, LocalDate desde, LocalDate hasta, ModeloPrediccion modelo) {
        StringBuilder sql = new StringBuilder("SELECT * FROM prediccion_demanda WHERE 1=1");
        MapSqlParameterSource p = new MapSqlParameterSource();
        if (productoId != null) {
            sql.append(" AND producto_id = :productoId");
            p.addValue("productoId", productoId);
        }
        if (desde != null) {
            sql.append(" AND fecha_objetivo >= :desde");
            p.addValue("desde", desde);
        }
        if (hasta != null) {
            sql.append(" AND fecha_objetivo <= :hasta");
            p.addValue("hasta", hasta);
        }
        if (modelo != null) {
            sql.append(" AND modelo = :modelo");
            p.addValue("modelo", modelo.name());
        }
        sql.append(" ORDER BY producto_id, fecha_objetivo, modelo LIMIT 5000");
        return jdbc.query(sql.toString(), p, (rs, i) -> new PrediccionResponse(
                rs.getLong("id"), rs.getLong("producto_id"), rs.getObject("fecha_objetivo", LocalDate.class),
                rs.getBigDecimal("cantidad_predicha"), rs.getBigDecimal("limite_inferior"),
                rs.getBigDecimal("limite_superior"), rs.getObject("stock_recomendado", Integer.class),
                rs.getObject("cantidad_a_pedir", Integer.class),
                ModeloPrediccion.valueOf(rs.getString("modelo")),
                rs.getTimestamp("generada_en").toLocalDateTime()));
    }

    /** Error contra ventas reales. Solo evalua fechas que ya pasaron. */
    @Transactional(readOnly = true)
    public PrecisionResponse precision(LocalDate desde, LocalDate hasta, ModeloPrediccion modelo) {
        LocalDate ayer = LocalDate.now().minusDays(1);
        LocalDate h = (hasta == null || hasta.isAfter(ayer)) ? ayer : hasta;
        LocalDate d = desde != null ? desde : h.minusDays(29);
        List<Punto> puntos = puntos(null, d, h, modelo);

        List<PrecisionResponse.PorModelo> modelos = new ArrayList<>();
        Map<ModeloPrediccion, List<Punto>> porModelo = puntos.stream()
                .collect(Collectors.groupingBy(Punto::modelo, TreeMap::new, Collectors.toList()));
        porModelo.forEach((m, lista) -> {
            Metricas g = Metricas.de(lista);
            List<PrecisionResponse.PorProducto> porProducto = lista.stream()
                    .collect(Collectors.groupingBy(Punto::productoId, LinkedHashMap::new, Collectors.toList()))
                    .values().stream()
                    .map(l -> {
                        Metricas mp = Metricas.de(l);
                        Punto x = l.get(0);
                        return new PrecisionResponse.PorProducto(x.productoId(), x.sku(), x.nombre(), l.size(),
                                Dinero.r2(mp.real), Dinero.r2(mp.predicho), mp.mape, mp.wape);
                    })
                    .sorted(Comparator.comparingDouble(PrecisionResponse.PorProducto::wape).reversed())
                    .toList();
            modelos.add(new PrecisionResponse.PorModelo(m, lista.size(), g.mape, g.wape, g.sesgo,
                    Dinero.r2(Math.max(0, 100 - g.wape)), porProducto));
        });
        return new PrecisionResponse(d, h, modelos);
    }

    /** BASE vs IA lado a lado para un producto. */
    @Transactional(readOnly = true)
    public List<ComparacionItem> comparar(Long productoId, LocalDate desde, LocalDate hasta) {
        if (productoId == null) {
            throw new ReglaNegocioException("productoId es obligatorio");
        }
        LocalDate d = desde != null ? desde : LocalDate.now().minusDays(14);
        LocalDate h = hasta != null ? hasta : LocalDate.now().plusDays(7);
        Map<LocalDate, double[]> filas = new TreeMap<>();
        // [real, base, baseMin, baseMax, ia, iaMin, iaMax]
        jdbc.query("""
                SELECT fecha_objetivo, modelo, cantidad_predicha, limite_inferior, limite_superior
                FROM prediccion_demanda
                WHERE producto_id = :pid AND fecha_objetivo BETWEEN :d AND :h
                """, new MapSqlParameterSource().addValue("pid", productoId).addValue("d", d).addValue("h", h), rs -> {
            double[] f = filas.computeIfAbsent(rs.getObject("fecha_objetivo", LocalDate.class), k -> nuevaFila());
            int off = "IA".equals(rs.getString("modelo")) ? 4 : 1;
            f[off] = rs.getDouble("cantidad_predicha");
            f[off + 1] = rs.getDouble("limite_inferior");
            f[off + 2] = rs.getDouble("limite_superior");
        });
        LocalDate hoy = LocalDate.now();
        SerieDiaria reales = estadisticas.series(d, h.isBefore(hoy) ? h : hoy.minusDays(1), List.of(productoId))
                .get(productoId);
        for (LocalDate f = d; !f.isAfter(h); f = f.plusDays(1)) {
            if (f.isBefore(hoy)) {
                filas.computeIfAbsent(f, k -> nuevaFila())[0] = reales == null ? 0 : reales.valor(f);
            }
        }
        List<ComparacionItem> r = new ArrayList<>();
        filas.forEach((f, v) -> r.add(new ComparacionItem(f, nan(v[0]), nan(v[1]), nan(v[2]), nan(v[3]),
                nan(v[4]), nan(v[5]), nan(v[6]))));
        return r;
    }

    private static double[] nuevaFila() {
        double[] f = new double[7];
        java.util.Arrays.fill(f, Double.NaN);
        return f;
    }

    private static Double nan(double v) {
        return Double.isNaN(v) ? null : v;
    }

    private List<Punto> puntos(Long productoId, LocalDate desde, LocalDate hasta, ModeloPrediccion modelo) {
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("desde", desde).addValue("hasta", hasta)
                .addValue("desdeTs", desde.atStartOfDay()).addValue("hastaTs", hasta.plusDays(1).atStartOfDay());
        String filtro = "";
        if (modelo != null) {
            filtro += " AND pd.modelo = :modelo";
            p.addValue("modelo", modelo.name());
        }
        if (productoId != null) {
            filtro += " AND pd.producto_id = :pid";
            p.addValue("pid", productoId);
        }
        return jdbc.query("""
                WITH reales AS (
                    SELECT producto_id, CAST(fecha_hora AS DATE) dia, SUM(cantidad) uds
                    FROM ventas WHERE fecha_hora >= :desdeTs AND fecha_hora < :hastaTs
                    GROUP BY producto_id, CAST(fecha_hora AS DATE))
                SELECT pd.modelo, pd.producto_id, p.sku, p.nombre, pd.fecha_objetivo,
                       pd.cantidad_predicha, COALESCE(r.uds, 0) real
                FROM prediccion_demanda pd
                JOIN productos p ON p.id = pd.producto_id
                LEFT JOIN reales r ON r.producto_id = pd.producto_id AND r.dia = pd.fecha_objetivo
                WHERE pd.fecha_objetivo BETWEEN :desde AND :hasta
                """ + filtro, p, (rs, i) -> new Punto(ModeloPrediccion.valueOf(rs.getString("modelo")),
                rs.getLong("producto_id"), rs.getString("sku"), rs.getString("nombre"),
                rs.getDouble("cantidad_predicha"), rs.getDouble("real")));
    }

    private record Punto(ModeloPrediccion modelo, Long productoId, String sku, String nombre, double predicho, double real) {
    }

    private record Metricas(double real, double predicho, double mape, double wape, double sesgo) {
        static Metricas de(List<Punto> puntos) {
            double sumReal = 0, sumPred = 0, sumAbs = 0, sumApe = 0;
            int nApe = 0;
            for (Punto p : puntos) {
                sumReal += p.real();
                sumPred += p.predicho();
                sumAbs += Math.abs(p.real() - p.predicho());
                if (p.real() > 0) {
                    sumApe += Math.abs(p.real() - p.predicho()) / p.real();
                    nApe++;
                }
            }
            double mape = nApe == 0 ? 0 : 100 * sumApe / nApe;
            double wape = sumReal == 0 ? 0 : 100 * sumAbs / sumReal;
            double sesgo = sumReal == 0 ? 0 : 100 * (sumPred - sumReal) / sumReal;
            return new Metricas(sumReal, sumPred, Dinero.r2(mape), Dinero.r2(wape), Dinero.r2(sesgo));
        }
    }
}
