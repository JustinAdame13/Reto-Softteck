package mx.Tienda_inteligente.pls.dashboard;

import mx.Tienda_inteligente.pls.common.Dinero;
import mx.Tienda_inteligente.pls.common.Fechas;
import mx.Tienda_inteligente.pls.configuracion.ConfiguracionService;
import mx.Tienda_inteligente.pls.inventario.AlertaInventario;
import mx.Tienda_inteligente.pls.inventario.EstadoProducto;
import mx.Tienda_inteligente.pls.inventario.InventarioService;
import mx.Tienda_inteligente.pls.prediccion.PrecisionResponse;
import mx.Tienda_inteligente.pls.prediccion.PrediccionService;
import mx.Tienda_inteligente.pls.promocion.EstadoPromocion;
import mx.Tienda_inteligente.pls.promocion.PromocionRepository;
import mx.Tienda_inteligente.pls.venta.VentaService;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;

/** Solo junta datos de los demas modulos para la pantalla principal. */
@Service
public class DashboardService {

    private final NamedParameterJdbcTemplate jdbc;
    private final InventarioService inventarioService;
    private final PromocionRepository promocionRepository;
    private final PrediccionService prediccionService;
    private final VentaService ventaService;
    private final ConfiguracionService configuracionService;

    public DashboardService(NamedParameterJdbcTemplate jdbc, InventarioService inventarioService,
                            PromocionRepository promocionRepository, PrediccionService prediccionService,
                            VentaService ventaService, ConfiguracionService configuracionService) {
        this.jdbc = jdbc;
        this.inventarioService = inventarioService;
        this.promocionRepository = promocionRepository;
        this.prediccionService = prediccionService;
        this.ventaService = ventaService;
        this.configuracionService = configuracionService;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> resumen(LocalDate desde, LocalDate hasta) {
        Fechas actual = Fechas.rango(desde, hasta, 30);
        Fechas anterior = actual.anterior();

        Map<String, Object> ventasActual = ventas(actual);
        Map<String, Object> ventasAnterior = ventas(anterior);
        BigDecimal perdidasActual = perdidas(actual);
        BigDecimal perdidasAnterior = perdidas(anterior);

        Map<String, Object> variacion = new LinkedHashMap<>();
        for (String k : List.of("ingresos", "margen", "unidades", "tickets", "ticketPromedio", "ventasConPromocion")) {
            variacion.put(k, variacion(num(ventasActual.get(k)), num(ventasAnterior.get(k))));
        }
        variacion.put("perdidas", variacion(perdidasActual.doubleValue(), perdidasAnterior.doubleValue()));

        List<EstadoProducto> estado = inventarioService.estado(null, null);
        Map<String, Long> semaforo = estado.stream()
                .collect(Collectors.groupingBy(e -> e.semaforo().name(), TreeMap::new, Collectors.counting()));
        BigDecimal valorInventario = estado.stream().map(EstadoProducto::valorInventario)
                .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<AlertaInventario> alertas = inventarioService.alertas();
        Map<String, Long> alertasPorTipo = alertas.stream()
                .collect(Collectors.groupingBy(a -> a.tipo().name(), TreeMap::new, Collectors.counting()));
        BigDecimal perdidaPotencialCaducidad = alertas.stream()
                .filter(a -> a.tipo() == AlertaInventario.TipoAlerta.POR_CADUCAR && a.perdidaPotencial() != null)
                .map(AlertaInventario::perdidaPotencial).reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, Object> inventario = new LinkedHashMap<>();
        inventario.put("productos", estado.size());
        inventario.put("valorInventario", Dinero.redondear(valorInventario));
        inventario.put("semaforo", semaforo);
        inventario.put("alertas", alertasPorTipo);
        inventario.put("perdidaPotencialPorCaducidad", Dinero.redondear(perdidaPotencialCaducidad));

        Map<String, Object> promociones = new LinkedHashMap<>();
        var sugeridas = promocionRepository.findByEstado(EstadoPromocion.SUGERIDA);
        promociones.put("activas", promocionRepository.findByEstado(EstadoPromocion.ACTIVA).size());
        promociones.put("sugeridas", sugeridas.size());
        promociones.put("gananciaEsperadaSugeridas", Dinero.redondear(sugeridas.stream()
                .map(p -> p.getGananciaEsperada() == null ? BigDecimal.ZERO : p.getGananciaEsperada())
                .reduce(BigDecimal.ZERO, BigDecimal::add)));

        Map<String, Object> modelo = new LinkedHashMap<>();
        PrecisionResponse precision = prediccionService.precision(null, null, null);
        precision.modelos().forEach(m -> modelo.put(m.modelo().name(), Map.of(
                "precision", m.precision(), "wape", m.wape(), "mape", m.mape(), "puntos", m.puntos())));

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("desde", actual.desde());
        r.put("hasta", actual.hasta());
        r.put("sucursal", configuracionService.obtenerEntidad().getNombreSucursal());
        r.put("iaActiva", configuracionService.iaActiva());
        r.put("ventas", ventasActual);
        r.put("periodoAnterior", Map.of("desde", anterior.desde(), "hasta", anterior.hasta(), "ventas", ventasAnterior,
                "perdidas", perdidasAnterior));
        r.put("perdidas", perdidasActual);
        r.put("variacionPorcentaje", variacion);
        r.put("inventario", inventario);
        r.put("promociones", promociones);
        r.put("precisionModelo", modelo);
        r.put("serieDiaria", ventaService.resumen(actual.desde(), actual.hasta(), "dia", null, null));
        return r;
    }

    private Map<String, Object> ventas(Fechas f) {
        Map<String, Object> fila = jdbc.queryForMap("""
                SELECT COALESCE(SUM(v.cantidad * v.precio_unitario - v.descuento_aplicado), 0) ingresos,
                       COALESCE(SUM(v.cantidad * p.costo), 0) costo,
                       COALESCE(SUM(v.cantidad), 0) unidades,
                       COUNT(DISTINCT v.ticket) tickets,
                       COALESCE(SUM(CASE WHEN v.promocion_id IS NOT NULL
                                THEN v.cantidad * v.precio_unitario - v.descuento_aplicado END), 0) con_promo,
                       COALESCE(SUM(v.descuento_aplicado), 0) descuento
                FROM ventas v JOIN productos p ON p.id = v.producto_id
                WHERE v.fecha_hora >= :desde AND v.fecha_hora < :hasta
                """, new MapSqlParameterSource()
                .addValue("desde", f.desde().atStartOfDay())
                .addValue("hasta", f.hasta().plusDays(1).atStartOfDay()));
        double ingresos = num(fila.get("ingresos"));
        double costo = num(fila.get("costo"));
        long tickets = (long) num(fila.get("tickets"));
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("ingresos", Dinero.de(ingresos));
        r.put("costo", Dinero.de(costo));
        r.put("margen", Dinero.de(ingresos - costo));
        r.put("margenPorcentaje", ingresos == 0 ? 0 : Dinero.r2(100 * (ingresos - costo) / ingresos));
        r.put("unidades", (long) num(fila.get("unidades")));
        r.put("tickets", tickets);
        r.put("ticketPromedio", tickets == 0 ? BigDecimal.ZERO : Dinero.de(ingresos / tickets));
        r.put("ventasConPromocion", Dinero.de(num(fila.get("con_promo"))));
        r.put("descuentoOtorgado", Dinero.de(num(fila.get("descuento"))));
        return r;
    }

    private BigDecimal perdidas(Fechas f) {
        BigDecimal v = jdbc.queryForObject("""
                SELECT COALESCE(SUM(costo_total), 0) FROM movimientos_inventario
                WHERE fecha >= :desde AND fecha < :hasta
                  AND (tipo IN ('MERMA', 'CADUCIDAD') OR (tipo = 'AJUSTE' AND cantidad < 0))
                """, new MapSqlParameterSource()
                .addValue("desde", f.desde().atStartOfDay())
                .addValue("hasta", f.hasta().plusDays(1).atStartOfDay()), BigDecimal.class);
        return Dinero.redondear(v == null ? BigDecimal.ZERO : v);
    }

    private static double num(Object o) {
        if (o instanceof Number n) return n.doubleValue();
        return o == null ? 0 : Double.parseDouble(o.toString());
    }

    private static Double variacion(double actual, double anterior) {
        if (anterior == 0) return null;
        return Dinero.r2(100 * (actual - anterior) / anterior);
    }
}
