package mx.Tienda_inteligente.pls.venta;

import mx.Tienda_inteligente.pls.common.Dinero;
import mx.Tienda_inteligente.pls.common.Fechas;
import mx.Tienda_inteligente.pls.common.FiltroSql;
import mx.Tienda_inteligente.pls.common.Pagina;
import mx.Tienda_inteligente.pls.common.ReglaNegocioException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Consultas de lectura sobre ventas con SQL directo (agregaciones con funciones de PostgreSQL).
 * Las escrituras con reglas de negocio viven en VentaService.
 */
@Component
public class VentaConsultas {

    static final String[] DIAS = {"", "Lunes", "Martes", "Miercoles", "Jueves", "Viernes", "Sabado", "Domingo"};

    /** Ingreso neto de una linea: cantidad x precio - descuento. */
    static final String INGRESO = "(v.cantidad * v.precio_unitario - v.descuento_aplicado)";

    private static final String SELECT_VENTA = """
            SELECT v.id, v.ticket, v.fecha_hora, v.producto_id, p.sku, p.nombre, v.cantidad,
                   v.precio_unitario, v.descuento_aplicado, v.promocion_id, v.empleado_id
            FROM ventas v JOIN productos p ON p.id = v.producto_id
            """;

    private static final RowMapper<VentaResponse> MAPPER = (rs, i) -> {
        BigDecimal precio = rs.getBigDecimal("precio_unitario");
        BigDecimal desc = rs.getBigDecimal("descuento_aplicado");
        int cantidad = rs.getInt("cantidad");
        return new VentaResponse(
                rs.getLong("id"), rs.getString("ticket"),
                rs.getTimestamp("fecha_hora").toLocalDateTime(),
                rs.getLong("producto_id"), rs.getString("sku"), rs.getString("nombre"),
                cantidad, precio, desc,
                precio.multiply(BigDecimal.valueOf(cantidad)).subtract(desc),
                rs.getObject("promocion_id", Long.class),
                rs.getObject("empleado_id", Long.class));
    };

    private final NamedParameterJdbcTemplate jdbc;

    public VentaConsultas(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Pagina<VentaResponse> listar(LocalDate desde, LocalDate hasta, Long productoId, Long empleadoId,
                                        Long promocionId, int page, int size) {
        FiltroSql f = new FiltroSql()
                .si(desde == null ? null : desde.atStartOfDay(), "v.fecha_hora >= :desde", "desde")
                .si(hasta == null ? null : hasta.plusDays(1).atStartOfDay(), "v.fecha_hora < :hasta", "hasta")
                .si(productoId, "v.producto_id = :productoId", "productoId")
                .si(empleadoId, "v.empleado_id = :empleadoId", "empleadoId")
                .si(promocionId, "v.promocion_id = :promocionId", "promocionId");
        int tam = Math.max(1, Math.min(size, 500));
        int pag = Math.max(0, page);
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM ventas v" + f.where(), f.params(), Long.class);
        f.param("limit", tam).param("offset", (long) pag * tam);
        List<VentaResponse> contenido = jdbc.query(
                SELECT_VENTA + f.where() + " ORDER BY v.fecha_hora DESC, v.id DESC LIMIT :limit OFFSET :offset",
                f.params(), MAPPER);
        return Pagina.of(contenido, pag, tam, total == null ? 0 : total);
    }

    public List<VentaResponse> porTicket(String ticket) {
        return jdbc.query(SELECT_VENTA + " WHERE v.ticket = :ticket ORDER BY v.id",
                new MapSqlParameterSource("ticket", ticket), MAPPER);
    }

    /**
     * Series agregadas. agrupar: dia, semana, mes, hora, diaSemana, producto, categoria.
     * Lo usan el frontend (graficas) y el motor de prediccion (historial).
     */
    public List<ResumenVentasItem> resumen(Fechas rango, String agrupar, Long productoId, String categoria) {
        String clave = agrupar == null ? "dia" : agrupar;
        String expr;
        String etiqueta = "CAST(NULL AS VARCHAR)";
        String orden = "1";
        switch (clave) {
            case "dia" -> expr = "to_char(v.fecha_hora, 'YYYY-MM-DD')";
            case "semana" -> expr = "to_char(date_trunc('week', v.fecha_hora), 'YYYY-MM-DD')";
            case "mes" -> expr = "to_char(v.fecha_hora, 'YYYY-MM')";
            case "hora" -> expr = "to_char(v.fecha_hora, 'HH24') || ':00'";
            case "diaSemana" -> expr = "CAST(EXTRACT(ISODOW FROM v.fecha_hora) AS VARCHAR)";
            case "producto" -> {
                expr = "p.sku";
                etiqueta = "p.nombre";
                orden = "ingresos DESC";
            }
            case "categoria" -> {
                expr = "p.categoria";
                orden = "ingresos DESC";
            }
            default -> throw new ReglaNegocioException("agrupar invalido: " + clave,
                    Map.of("agrupar", "Usa dia, semana, mes, hora, diaSemana, producto o categoria"));
        }

        FiltroSql f = new FiltroSql()
                .siempre("v.fecha_hora >= :desde").param("desde", rango.desde().atStartOfDay())
                .siempre("v.fecha_hora < :hasta").param("hasta", rango.hasta().plusDays(1).atStartOfDay())
                .si(productoId, "v.producto_id = :productoId", "productoId")
                .si(categoria, "p.categoria = :categoria", "categoria");

        String sql = "SELECT " + expr + " AS periodo, " + etiqueta + " AS etiqueta,"
                + " SUM(v.cantidad) AS unidades,"
                + " SUM" + INGRESO + " AS ingresos,"
                + " SUM(v.cantidad * p.costo) AS costo,"
                + " COUNT(DISTINCT v.ticket) AS tickets"
                + " FROM ventas v JOIN productos p ON p.id = v.producto_id"
                + f.where()
                + " GROUP BY " + expr + ", " + etiqueta
                + " ORDER BY " + orden;

        List<ResumenVentasItem> filas = jdbc.query(sql, f.params(), (rs, i) -> {
            BigDecimal ingresos = Dinero.redondear(Dinero.nz(rs.getBigDecimal("ingresos")));
            BigDecimal costo = Dinero.redondear(Dinero.nz(rs.getBigDecimal("costo")));
            String periodo = rs.getString("periodo");
            String et = rs.getString("etiqueta");
            if ("diaSemana".equals(clave)) {
                et = DIAS[Integer.parseInt(periodo)];
            }
            return new ResumenVentasItem(periodo, et, rs.getLong("unidades"), ingresos, costo,
                    ingresos.subtract(costo), rs.getLong("tickets"));
        });

        if ("dia".equals(clave)) {
            return rellenarDias(filas, rango);
        }
        return filas;
    }

    /** Agrega los dias sin ventas con ceros, para que las graficas y el algoritmo no tengan huecos. */
    private List<ResumenVentasItem> rellenarDias(List<ResumenVentasItem> filas, Fechas rango) {
        Map<String, ResumenVentasItem> porDia = new LinkedHashMap<>();
        filas.forEach(r -> porDia.put(r.periodo(), r));
        List<ResumenVentasItem> completo = new ArrayList<>();
        for (LocalDate d = rango.desde(); !d.isAfter(rango.hasta()); d = d.plusDays(1)) {
            String k = d.toString();
            completo.add(porDia.getOrDefault(k,
                    new ResumenVentasItem(k, null, 0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0)));
        }
        return completo;
    }

    public RecordsVentas records(Fechas rango) {
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("desde", rango.desde().atStartOfDay())
                .addValue("hasta", rango.hasta().plusDays(1).atStartOfDay());
        String w = " WHERE v.fecha_hora >= :desde AND v.fecha_hora < :hasta ";

        RecordsVentas.Totales totales = jdbc.queryForObject(
                "SELECT COALESCE(SUM" + INGRESO + ",0) ing, COALESCE(SUM(v.cantidad*p.costo),0) costo,"
                        + " COALESCE(SUM(v.cantidad),0) uds, COUNT(DISTINCT v.ticket) tk"
                        + " FROM ventas v JOIN productos p ON p.id = v.producto_id" + w, p,
                (rs, i) -> {
                    BigDecimal ing = Dinero.redondear(rs.getBigDecimal("ing"));
                    long tk = rs.getLong("tk");
                    return new RecordsVentas.Totales(ing,
                            Dinero.redondear(rs.getBigDecimal("ing").subtract(rs.getBigDecimal("costo"))),
                            rs.getLong("uds"), tk,
                            tk == 0 ? BigDecimal.ZERO : Dinero.de(ing.doubleValue() / tk));
                });

        RecordsVentas.Dia mejorDia = primero(jdbc.query(
                "SELECT CAST(v.fecha_hora AS DATE) dia, SUM" + INGRESO + " ing, COUNT(DISTINCT v.ticket) tk"
                        + " FROM ventas v" + w + " GROUP BY 1 ORDER BY ing DESC LIMIT 1", p,
                (rs, i) -> new RecordsVentas.Dia(rs.getObject("dia", LocalDate.class),
                        Dinero.redondear(rs.getBigDecimal("ing")), rs.getLong("tk"))));

        RecordsVentas.Hora mejorHora = primero(jdbc.query(
                "SELECT CAST(EXTRACT(HOUR FROM v.fecha_hora) AS INT) h, SUM" + INGRESO + " ing,"
                        + " COUNT(DISTINCT v.ticket) tk, COUNT(DISTINCT CAST(v.fecha_hora AS DATE)) dias"
                        + " FROM ventas v" + w + " GROUP BY 1 ORDER BY ing DESC LIMIT 1", p,
                (rs, i) -> new RecordsVentas.Hora(rs.getInt("h"), Dinero.redondear(rs.getBigDecimal("ing")),
                        rs.getLong("tk"), Dinero.r2((double) rs.getLong("tk") / Math.max(1, rs.getLong("dias"))))));

        RecordsVentas.DiaSemana mejorDiaSemana = primero(jdbc.query(
                "SELECT CAST(EXTRACT(ISODOW FROM v.fecha_hora) AS INT) d, SUM" + INGRESO + " ing,"
                        + " COUNT(DISTINCT v.ticket) tk FROM ventas v" + w + " GROUP BY 1 ORDER BY ing DESC LIMIT 1", p,
                (rs, i) -> new RecordsVentas.DiaSemana(rs.getInt("d"), DIAS[rs.getInt("d")],
                        Dinero.redondear(rs.getBigDecimal("ing")), rs.getLong("tk"))));

        String sqlProducto = "SELECT p.id, p.sku, p.nombre, SUM(v.cantidad) uds, SUM" + INGRESO + " ing,"
                + " SUM" + INGRESO + " - SUM(v.cantidad*p.costo) mg"
                + " FROM ventas v JOIN productos p ON p.id = v.producto_id" + w
                + " GROUP BY p.id, p.sku, p.nombre ORDER BY %s DESC LIMIT 1";
        RowMapper<RecordsVentas.ProductoTop> prodMapper = (rs, i) -> new RecordsVentas.ProductoTop(
                rs.getLong("id"), rs.getString("sku"), rs.getString("nombre"), rs.getLong("uds"),
                Dinero.redondear(rs.getBigDecimal("ing")), Dinero.redondear(rs.getBigDecimal("mg")));
        RecordsVentas.ProductoTop estrella = primero(jdbc.query(sqlProducto.formatted("uds"), p, prodMapper));
        RecordsVentas.ProductoTop rentable = primero(jdbc.query(sqlProducto.formatted("mg"), p, prodMapper));

        RecordsVentas.EmpleadoTop mejorEmpleado = primero(jdbc.query(
                "SELECT e.id, e.nombre, SUM" + INGRESO + " ing, COUNT(DISTINCT v.ticket) tk"
                        + " FROM ventas v JOIN empleados e ON e.id = v.empleado_id" + w
                        + " GROUP BY e.id, e.nombre ORDER BY ing DESC LIMIT 1", p,
                (rs, i) -> new RecordsVentas.EmpleadoTop(rs.getLong("id"), rs.getString("nombre"),
                        Dinero.redondear(rs.getBigDecimal("ing")), rs.getLong("tk"))));

        RecordsVentas.TicketTop ticketTop = primero(jdbc.query(
                "SELECT v.ticket, CAST(MIN(v.fecha_hora) AS DATE) dia, SUM" + INGRESO + " total, SUM(v.cantidad) art"
                        + " FROM ventas v" + w + " GROUP BY v.ticket ORDER BY total DESC LIMIT 1", p,
                (rs, i) -> new RecordsVentas.TicketTop(rs.getString("ticket"), rs.getObject("dia", LocalDate.class),
                        Dinero.redondear(rs.getBigDecimal("total")), rs.getLong("art"))));

        return new RecordsVentas(rango.desde(), rango.hasta(), totales, mejorDia, mejorHora, mejorDiaSemana,
                estrella, rentable, mejorEmpleado, ticketTop);
    }

    /** Inserta filas historicas en bloques (mucho mas rapido que JPA para miles de filas). */
    public int insertarLote(List<Object[]> filas) {
        String sql = "INSERT INTO ventas (ticket, fecha_hora, producto_id, cantidad, precio_unitario,"
                + " descuento_aplicado, promocion_id, empleado_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        int total = 0;
        for (int i = 0; i < filas.size(); i += 1000) {
            List<Object[]> bloque = filas.subList(i, Math.min(filas.size(), i + 1000));
            jdbc.getJdbcTemplate().batchUpdate(sql, bloque);
            total += bloque.size();
        }
        return total;
    }

    static Timestamp ts(LocalDateTime t) {
        return Timestamp.valueOf(t);
    }

    private static <T> T primero(List<T> lista) {
        return lista.isEmpty() ? null : lista.get(0);
    }
}
