package mx.Tienda_inteligente.pls.inventario;

import mx.Tienda_inteligente.pls.common.Dinero;
import mx.Tienda_inteligente.pls.common.Fechas;
import mx.Tienda_inteligente.pls.common.FiltroSql;
import mx.Tienda_inteligente.pls.common.Pagina;
import mx.Tienda_inteligente.pls.common.RecursoNoEncontradoException;
import mx.Tienda_inteligente.pls.common.ReglaNegocioException;
import mx.Tienda_inteligente.pls.configuracion.ConfiguracionService;
import mx.Tienda_inteligente.pls.inventario.AlertaInventario.TipoAlerta;
import mx.Tienda_inteligente.pls.producto.Producto;
import mx.Tienda_inteligente.pls.producto.ProductoRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class InventarioService {

    private final MovimientoRepository movimientoRepository;
    private final ProductoRepository productoRepository;
    private final AnalisisInventario analisis;
    private final ConfiguracionService configuracionService;
    private final NamedParameterJdbcTemplate jdbc;

    public InventarioService(MovimientoRepository movimientoRepository, ProductoRepository productoRepository,
                             AnalisisInventario analisis, ConfiguracionService configuracionService,
                             NamedParameterJdbcTemplate jdbc) {
        this.movimientoRepository = movimientoRepository;
        this.productoRepository = productoRepository;
        this.analisis = analisis;
        this.configuracionService = configuracionService;
        this.jdbc = jdbc;
    }

    /** Registra el movimiento y actualiza el stock en la misma transaccion. */
    @Transactional
    public MovimientoResponse registrar(MovimientoRequest r) {
        Producto p = productoRepository.findById(r.productoId())
                .orElseThrow(() -> RecursoNoEncontradoException.de("Producto", r.productoId()));
        MovimientoInventario m = construir(r, p);
        int delta = r.tipo().delta(r.cantidad());
        int nuevoStock = p.getStockActual() + delta;
        if (nuevoStock < 0) {
            throw new ReglaNegocioException("El movimiento dejaria el stock en negativo",
                    Map.of("cantidad", "Stock actual: " + p.getStockActual()));
        }
        m = movimientoRepository.save(m);
        p.setStockActual(nuevoStock);
        return respuesta(m, p, delta, nuevoStock);
    }

    /**
     * Carga historica de movimientos (por ejemplo, mermas de los ultimos meses para el dashboard).
     * Por defecto NO toca el stock, igual que /ventas/lote.
     */
    @Transactional
    public Map<String, Object> registrarLote(List<MovimientoRequest> lote, boolean afectarStock) {
        Map<Long, Producto> productos = new HashMap<>();
        productoRepository.findAll().forEach(p -> productos.put(p.getId(), p));
        List<MovimientoInventario> nuevos = new ArrayList<>();
        for (int i = 0; i < lote.size(); i++) {
            MovimientoRequest r = lote.get(i);
            Producto p = productos.get(r.productoId());
            if (p == null) {
                throw new ReglaNegocioException("Fila " + i + ": producto " + r.productoId() + " no existe");
            }
            nuevos.add(construir(r, p));
            if (afectarStock) {
                p.setStockActual(Math.max(0, p.getStockActual() + r.tipo().delta(r.cantidad())));
            }
        }
        movimientoRepository.saveAll(nuevos);
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("insertados", nuevos.size());
        resp.put("afectoStock", afectarStock);
        return resp;
    }

    private MovimientoInventario construir(MovimientoRequest r, Producto p) {
        if (r.cantidad() == 0) {
            throw new ReglaNegocioException("La cantidad no puede ser 0", Map.of("cantidad", "Distinta de 0"));
        }
        if (r.tipo() != TipoMovimiento.AJUSTE && r.cantidad() < 0) {
            throw new ReglaNegocioException("La cantidad debe ser positiva; el signo lo decide el tipo",
                    Map.of("cantidad", "Solo AJUSTE acepta negativos"));
        }
        MovimientoInventario m = new MovimientoInventario();
        m.setProductoId(p.getId());
        m.setTipo(r.tipo());
        m.setCantidad(r.cantidad());
        m.setFecha(r.fecha() != null ? r.fecha() : LocalDateTime.now());
        m.setMotivo(r.motivo());
        m.setCostoTotal(r.costoTotal() != null ? r.costoTotal()
                : Dinero.redondear(p.getCosto().multiply(BigDecimal.valueOf(Math.abs(r.cantidad())))));
        return m;
    }

    private MovimientoResponse respuesta(MovimientoInventario m, Producto p, Integer delta, Integer stock) {
        return new MovimientoResponse(m.getId(), p.getId(), p.getSku(), p.getNombre(), m.getFecha(), m.getTipo(),
                m.getCantidad(), delta, m.getCostoTotal(), m.getMotivo(), stock);
    }

    @Transactional(readOnly = true)
    public Pagina<MovimientoResponse> listar(Long productoId, TipoMovimiento tipo, LocalDate desde, LocalDate hasta,
                                             int page, int size) {
        FiltroSql f = new FiltroSql()
                .si(productoId, "m.producto_id = :productoId", "productoId")
                .si(tipo == null ? null : tipo.name(), "m.tipo = :tipo", "tipo")
                .si(desde == null ? null : desde.atStartOfDay(), "m.fecha >= :desde", "desde")
                .si(hasta == null ? null : hasta.plusDays(1).atStartOfDay(), "m.fecha < :hasta", "hasta");
        int tam = Math.max(1, Math.min(size, 500));
        int pag = Math.max(0, page);
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM movimientos_inventario m" + f.where(), f.params(), Long.class);
        f.param("limit", tam).param("offset", (long) pag * tam);
        List<MovimientoResponse> contenido = jdbc.query("""
                SELECT m.id, m.producto_id, p.sku, p.nombre, m.fecha, m.tipo, m.cantidad, m.costo_total, m.motivo
                FROM movimientos_inventario m JOIN productos p ON p.id = m.producto_id
                """ + f.where() + " ORDER BY m.fecha DESC, m.id DESC LIMIT :limit OFFSET :offset", f.params(),
                (rs, i) -> {
                    TipoMovimiento t = TipoMovimiento.valueOf(rs.getString("tipo"));
                    int cant = rs.getInt("cantidad");
                    return new MovimientoResponse(rs.getLong("id"), rs.getLong("producto_id"), rs.getString("sku"),
                            rs.getString("nombre"), rs.getTimestamp("fecha").toLocalDateTime(), t, cant,
                            t.delta(cant), rs.getBigDecimal("costo_total"), rs.getString("motivo"), null);
                });
        return Pagina.of(contenido, pag, tam, total == null ? 0 : total);
    }

    @Transactional(readOnly = true)
    public List<EstadoProducto> estado(Semaforo semaforo, String categoria) {
        return analisis.calcular().stream()
                .filter(e -> semaforo == null || e.semaforo() == semaforo)
                .filter(e -> categoria == null || categoria.isBlank() || e.categoria().equalsIgnoreCase(categoria))
                .sorted(Comparator.comparing(EstadoProducto::semaforo).thenComparing(EstadoProducto::nombre))
                .toList();
    }

    /** Bajo stock, agotado, sobrestock, por caducar y sin movimiento. */
    @Transactional(readOnly = true)
    public List<AlertaInventario> alertas() {
        List<AlertaInventario> alertas = new ArrayList<>();
        for (EstadoProducto e : analisis.calcular()) {
            int objetivo = objetivo(e);
            switch (e.semaforo()) {
                case AGOTADO -> alertas.add(new AlertaInventario(e.productoId(), e.sku(), e.nombre(), TipoAlerta.AGOTADO,
                        "ALTA", e.stockActual(), e.puntoReorden(), objetivo, null, null,
                        Dinero.de(e.demandaDiaria() * e.leadTimeDias() * margenUnitario(e)),
                        "Agotado. Pedir " + objetivo + " unidades (se pierden ~$"
                                + Dinero.r2(e.demandaDiaria() * margenUnitario(e)) + " de margen por dia)"));
                case CRITICO, BAJO -> alertas.add(new AlertaInventario(e.productoId(), e.sku(), e.nombre(),
                        TipoAlerta.BAJO_STOCK, e.semaforo() == Semaforo.CRITICO ? "ALTA" : "MEDIA",
                        e.stockActual(), e.puntoReorden(), Math.max(0, objetivo - e.stockActual()), null, null, null,
                        "Pedir " + Math.max(0, objetivo - e.stockActual()) + " unidades"
                                + (e.diasCobertura() != null ? " (alcanza para " + e.diasCobertura() + " dias)" : "")));
                case SOBRESTOCK -> alertas.add(new AlertaInventario(e.productoId(), e.sku(), e.nombre(),
                        TipoAlerta.SOBRESTOCK, "BAJA", e.stockActual(), e.puntoReorden(), null, null, null,
                        e.valorInventario(),
                        "Sobrestock: " + (e.diasCobertura() != null ? e.diasCobertura() + " dias de cobertura" : "sin ventas")
                                + ", $" + e.valorInventario() + " detenidos. Candidato a promocion"));
                default -> {
                }
            }
            if (e.unidadesEnRiesgo() != null && e.unidadesEnRiesgo() > 0 && e.diasParaCaducar() != null
                    && e.diasParaCaducar() <= 7) {
                BigDecimal perdida = Dinero.redondear(e.costoUnitario().multiply(BigDecimal.valueOf(e.unidadesEnRiesgo())));
                alertas.add(new AlertaInventario(e.productoId(), e.sku(), e.nombre(), TipoAlerta.POR_CADUCAR,
                        e.diasParaCaducar() <= 2 ? "ALTA" : "MEDIA", e.stockActual(), null, null,
                        e.diasParaCaducar(), e.unidadesEnRiesgo(), perdida,
                        e.unidadesEnRiesgo() + " unidades caducan en " + Math.max(0, e.diasParaCaducar())
                                + " dias (perdida potencial $" + perdida + ")"));
            }
            if (e.stockActual() > 0 && e.demandaDiaria() == 0 && e.diasSinVenta() >= AnalisisInventario.VENTANA_DIAS) {
                alertas.add(new AlertaInventario(e.productoId(), e.sku(), e.nombre(), TipoAlerta.SIN_MOVIMIENTO,
                        "MEDIA", e.stockActual(), null, null, null, null, e.valorInventario(),
                        "Sin ventas en " + AnalisisInventario.VENTANA_DIAS + " dias; $" + e.valorInventario() + " detenidos"));
            }
        }
        alertas.sort(Comparator.comparing((AlertaInventario a) -> switch (a.severidad()) {
            case "ALTA" -> 0;
            case "MEDIA" -> 1;
            default -> 2;
        }).thenComparing(AlertaInventario::nombre));
        return alertas;
    }

    /**
     * Que pedir y cuanto. Demanda del horizonte (leadTime + 7 dias) tomada de las predicciones guardadas
     * (IA si esta activa, si no BASE); si no hay predicciones, del promedio historico.
     */
    @Transactional(readOnly = true)
    public List<ReabastoItem> reabasto() {
        LocalDate hoy = LocalDate.now();
        boolean ia = configuracionService.iaActiva();
        Map<Long, Map<String, double[]>> predicciones = prediccionesFuturas(hoy, hoy.plusDays(60));
        List<ReabastoItem> items = new ArrayList<>();
        for (EstadoProducto e : analisis.calcular()) {
            int horizonte = e.leadTimeDias() + 7;
            double demanda;
            String fuente;
            Map<String, double[]> porModelo = predicciones.get(e.productoId());
            double[] serie = porModelo == null ? null : (ia && porModelo.containsKey("IA") ? porModelo.get("IA") : porModelo.get("BASE"));
            if (serie != null) {
                demanda = 0;
                for (int i = 0; i < horizonte && i < serie.length; i++) {
                    demanda += Double.isNaN(serie[i]) ? e.demandaDiaria() : serie[i];
                }
                fuente = (ia && porModelo.containsKey("IA")) ? "PREDICCION_IA" : "PREDICCION_BASE";
            } else {
                demanda = e.demandaDiaria() * horizonte;
                fuente = "PROMEDIO_HISTORICO";
            }
            int pedir = (int) Math.ceil(demanda + e.stockSeguridad() - e.stockActual());
            if (e.stockMaximo() != null) {
                pedir = Math.min(pedir, e.stockMaximo() - e.stockActual());
            }
            // pedir > 0 significa que, en el horizonte, el stock caeria por debajo del de seguridad
            if (pedir <= 0) {
                continue;
            }
            double diaria = demanda / horizonte;
            LocalDate pedirAntes = diaria > 0
                    ? hoy.plusDays((long) Math.max(0, Math.floor((e.stockActual() - e.stockSeguridad()) / diaria) - e.leadTimeDias()))
                    : hoy;
            String prioridad = e.semaforo() == Semaforo.AGOTADO || e.semaforo() == Semaforo.CRITICO ? "ALTA"
                    : (pedirAntes.isAfter(hoy.plusDays(2)) ? "BAJA" : "MEDIA");
            items.add(new ReabastoItem(e.productoId(), e.sku(), e.nombre(), e.categoria(), e.stockActual(),
                    e.stockSeguridad(), Dinero.r2(demanda), horizonte, pedir,
                    Dinero.redondear(e.costoUnitario().multiply(BigDecimal.valueOf(pedir))), pedirAntes, fuente, prioridad));
        }
        items.sort(Comparator.comparing(ReabastoItem::pedirAntesDe).thenComparing(ReabastoItem::nombre));
        return items;
    }

    /** productoId -> modelo -> cantidades predichas desde 'desde' (NaN si falta el dia). */
    private Map<Long, Map<String, double[]>> prediccionesFuturas(LocalDate desde, LocalDate hasta) {
        int n = (int) java.time.temporal.ChronoUnit.DAYS.between(desde, hasta) + 1;
        Map<Long, Map<String, double[]>> r = new HashMap<>();
        jdbc.query("""
                SELECT producto_id, modelo, fecha_objetivo, cantidad_predicha
                FROM prediccion_demanda WHERE fecha_objetivo BETWEEN :desde AND :hasta
                """, new MapSqlParameterSource().addValue("desde", desde).addValue("hasta", hasta), rs -> {
            double[] serie = r.computeIfAbsent(rs.getLong("producto_id"), k -> new HashMap<>())
                    .computeIfAbsent(rs.getString("modelo"), k -> {
                        double[] a = new double[n];
                        java.util.Arrays.fill(a, Double.NaN);
                        return a;
                    });
            int i = (int) java.time.temporal.ChronoUnit.DAYS.between(desde, rs.getObject("fecha_objetivo", LocalDate.class));
            if (i >= 0 && i < n) serie[i] = rs.getDouble("cantidad_predicha");
        });
        return r;
    }

    /** Perdidas en dinero: mermas, caducidades y ajustes negativos (faltantes). */
    @Transactional(readOnly = true)
    public Map<String, Object> perdidas(LocalDate desde, LocalDate hasta) {
        Fechas rango = Fechas.rango(desde, hasta, 30);
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("desde", rango.desde().atStartOfDay())
                .addValue("hasta", rango.hasta().plusDays(1).atStartOfDay());
        String base = " FROM movimientos_inventario m JOIN productos pr ON pr.id = m.producto_id"
                + " WHERE m.fecha >= :desde AND m.fecha < :hasta"
                + " AND (m.tipo IN ('MERMA','CADUCIDAD') OR (m.tipo = 'AJUSTE' AND m.cantidad < 0))";

        Map<String, BigDecimal> porTipo = new LinkedHashMap<>();
        porTipo.put("MERMA", BigDecimal.ZERO);
        porTipo.put("CADUCIDAD", BigDecimal.ZERO);
        porTipo.put("AJUSTE", BigDecimal.ZERO);
        jdbc.query("SELECT m.tipo, SUM(m.costo_total) total" + base + " GROUP BY m.tipo", p,
                rs -> {
                    porTipo.put(rs.getString("tipo"), Dinero.redondear(rs.getBigDecimal("total")));
                });

        List<Map<String, Object>> porCategoria = jdbc.query(
                "SELECT pr.categoria, SUM(m.costo_total) total, SUM(ABS(m.cantidad)) unidades" + base
                        + " GROUP BY pr.categoria ORDER BY total DESC", p,
                (rs, i) -> fila("categoria", rs.getString("categoria"), "total",
                        Dinero.redondear(rs.getBigDecimal("total")), "unidades", rs.getLong("unidades")));

        List<Map<String, Object>> topProductos = jdbc.query(
                "SELECT pr.id, pr.sku, pr.nombre, SUM(m.costo_total) total, SUM(ABS(m.cantidad)) unidades" + base
                        + " GROUP BY pr.id, pr.sku, pr.nombre ORDER BY total DESC LIMIT 10", p,
                (rs, i) -> {
                    Map<String, Object> f = fila("productoId", rs.getLong("id"), "sku", rs.getString("sku"),
                            "total", Dinero.redondear(rs.getBigDecimal("total")));
                    f.put("nombre", rs.getString("nombre"));
                    f.put("unidades", rs.getLong("unidades"));
                    return f;
                });

        List<Map<String, Object>> porDia = jdbc.query(
                "SELECT CAST(m.fecha AS DATE) dia, SUM(m.costo_total) total" + base + " GROUP BY 1 ORDER BY 1", p,
                (rs, i) -> {
                    Map<String, Object> f = new LinkedHashMap<>();
                    f.put("fecha", rs.getObject("dia", LocalDate.class).toString());
                    f.put("total", Dinero.redondear(rs.getBigDecimal("total")));
                    return f;
                });

        BigDecimal total = porTipo.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("desde", rango.desde());
        r.put("hasta", rango.hasta());
        r.put("total", total);
        r.put("porTipo", porTipo);
        r.put("porCategoria", porCategoria);
        r.put("topProductos", topProductos);
        r.put("porDia", porDia);
        return r;
    }

    private static int objetivo(EstadoProducto e) {
        int obj = e.stockMaximo() != null ? e.stockMaximo()
                : (int) Math.ceil(e.puntoReorden() + e.demandaDiaria() * 7);
        return Math.max(obj, e.stockMinimo());
    }

    private static double margenUnitario(EstadoProducto e) {
        return e.precioVenta().subtract(e.costoUnitario()).doubleValue();
    }

    private static Map<String, Object> fila(String k1, Object v1, String k2, Object v2, String k3, Object v3) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put(k1, v1);
        m.put(k2, v2);
        m.put(k3, v3);
        return m;
    }
}
