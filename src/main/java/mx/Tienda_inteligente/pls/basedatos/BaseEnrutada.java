package mx.Tienda_inteligente.pls.basedatos;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

/**
 * DataSource "intermediario": cada vez que JPA o JdbcTemplate piden una conexion,
 * pregunta a SelectorBase que base toca y le pasa la peticion al pool real.
 */
public class BaseEnrutada extends AbstractRoutingDataSource implements DisposableBean {

    private final SelectorBase selector;

    public BaseEnrutada(SelectorBase selector) {
        this.selector = selector;
    }

    @Override
    protected Object determineCurrentLookupKey() {
        return selector.actual();
    }

    /** Cierra los 3 pools al apagar (importante con devtools, que reinicia seguido). */
    @Override
    public void destroy() {
        getResolvedDataSources().values().forEach(ds -> {
            if (ds instanceof HikariDataSource hikari) {
                hikari.close();
            }
        });
    }
}
