package mx.Tienda_inteligente.pls.analitica;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Lee el historial de ventas agrupado por producto y dia.
 * Es la base comun de inventario, prediccion y promociones.
 */
@Component
public class EstadisticasVentas {

    private final NamedParameterJdbcTemplate jdbc;

    public EstadisticasVentas(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Series diarias [desde, hasta] (ambos inclusive) para los productos indicados
     * (todos si productoIds es null o vacio). Productos sin ventas no aparecen en el mapa:
     * usa {@link #serieVacia} como valor por defecto.
     */
    public Map<Long, SerieDiaria> series(LocalDate desde, LocalDate hasta, Collection<Long> productoIds) {
        int n = (int) ChronoUnit.DAYS.between(desde, hasta) + 1;
        if (n <= 0) return Map.of();

        StringBuilder sql = new StringBuilder("""
                SELECT producto_id, CAST(fecha_hora AS DATE) AS dia, SUM(cantidad) AS unidades
                FROM ventas
                WHERE fecha_hora >= :desde AND fecha_hora < :hastaExcl
                """);
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("desde", desde.atStartOfDay())
                .addValue("hastaExcl", hasta.plusDays(1).atStartOfDay());
        if (productoIds != null && !productoIds.isEmpty()) {
            sql.append(" AND producto_id IN (:ids)");
            p.addValue("ids", productoIds);
        }
        sql.append(" GROUP BY producto_id, CAST(fecha_hora AS DATE)");

        Map<Long, double[]> datos = new HashMap<>();
        jdbc.query(sql.toString(), p, rs -> {
            long id = rs.getLong("producto_id");
            LocalDate dia = rs.getObject("dia", LocalDate.class);
            int i = (int) ChronoUnit.DAYS.between(desde, dia);
            if (i >= 0 && i < n) {
                datos.computeIfAbsent(id, k -> new double[n])[i] = rs.getDouble("unidades");
            }
        });

        Map<Long, SerieDiaria> resultado = new HashMap<>();
        datos.forEach((id, valores) -> resultado.put(id, new SerieDiaria(id, desde, valores)));
        return resultado;
    }

    /**
     * Marca, por producto, los dias [desde, hasta] en que hubo promocion: ventas con
     * promocion_id o con descuento, o una promocion ACTIVA/FINALIZADA vigente ese dia.
     * El motor de prediccion excluye esos dias porque inflan la demanda "normal".
     * Productos sin promociones no aparecen en el mapa.
     */
    public Map<Long, boolean[]> diasConPromocion(LocalDate desde, LocalDate hasta, Collection<Long> productoIds) {
        int n = (int) ChronoUnit.DAYS.between(desde, hasta) + 1;
        if (n <= 0) return Map.of();

        boolean filtrar = productoIds != null && !productoIds.isEmpty();
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("desde", desde)
                .addValue("hasta", hasta)
                .addValue("desdeTs", desde.atStartOfDay())
                .addValue("hastaExcl", hasta.plusDays(1).atStartOfDay());
        if (filtrar) p.addValue("ids", productoIds);

        Map<Long, boolean[]> r = new HashMap<>();

        // 1) Lo que realmente se vendio con promocion o con descuento
        jdbc.query("""
                SELECT DISTINCT producto_id, CAST(fecha_hora AS DATE) AS dia
                FROM ventas
                WHERE fecha_hora >= :desdeTs AND fecha_hora < :hastaExcl
                  AND (promocion_id IS NOT NULL OR descuento_aplicado > 0)
                """ + (filtrar ? " AND producto_id IN (:ids)" : ""), p, rs -> {
            int i = (int) ChronoUnit.DAYS.between(desde, rs.getObject("dia", LocalDate.class));
            if (i >= 0 && i < n) {
                r.computeIfAbsent(rs.getLong("producto_id"), k -> new boolean[n])[i] = true;
            }
        });

        // 2) Promociones vigentes aunque la venta no traiga promocion_id (cargas por lote)
        jdbc.query("""
                SELECT producto_id, fecha_inicio, fecha_fin
                FROM promociones
                WHERE estado IN ('ACTIVA', 'FINALIZADA')
                  AND fecha_inicio <= :hasta AND fecha_fin >= :desde
                """ + (filtrar ? " AND producto_id IN (:ids)" : ""), p, rs -> {
            LocalDate ini = rs.getObject("fecha_inicio", LocalDate.class);
            LocalDate fin = rs.getObject("fecha_fin", LocalDate.class);
            int a = (int) Math.max(0, ChronoUnit.DAYS.between(desde, ini));
            int b = (int) Math.min(n - 1, ChronoUnit.DAYS.between(desde, fin));
            if (a > b) return;
            boolean[] m = r.computeIfAbsent(rs.getLong("producto_id"), k -> new boolean[n]);
            for (int i = a; i <= b; i++) m[i] = true;
        });
        return r;
    }

    public SerieDiaria serieVacia(Long productoId, LocalDate desde, LocalDate hasta) {
        int n = (int) Math.max(0, ChronoUnit.DAYS.between(desde, hasta) + 1);
        return new SerieDiaria(productoId, desde, new double[n]);
    }

    /** Fecha de la ultima ENTRADA de inventario por producto (para estimar caducidad sin lotes). */
    public Map<Long, LocalDate> ultimaEntrada() {
        Map<Long, LocalDate> r = new HashMap<>();
        jdbc.query("""
                SELECT producto_id, CAST(MAX(fecha) AS DATE) AS ultima
                FROM movimientos_inventario
                WHERE tipo = 'ENTRADA'
                GROUP BY producto_id
                """, rs -> {
            r.put(rs.getLong("producto_id"), rs.getObject("ultima", LocalDate.class));
        });
        return r;
    }
}
