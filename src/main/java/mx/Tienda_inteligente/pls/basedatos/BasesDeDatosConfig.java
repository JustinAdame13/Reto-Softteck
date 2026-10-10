package mx.Tienda_inteligente.pls.basedatos;

import com.zaxxer.hikari.HikariDataSource;
import mx.Tienda_inteligente.pls.configuracion.Tamano;
import org.flywaydb.core.Flyway;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import javax.sql.DataSource;
import java.util.EnumMap;
import java.util.Map;

/**
 * Crea un pool por sucursal (PEQUENA, MEDIANA, GRANDE) y los envuelve en BaseEnrutada.
 *
 * Flyway se corre aqui a mano (spring.flyway.enabled=false): el automatico solo
 * migraria la base activa al arrancar y las otras dos quedarian sin tablas, y
 * ddl-auto=validate tronaria al cambiar a ellas. Hacerlo dentro de este @Bean
 * garantiza que las 3 esten al dia ANTES de que Hibernate valide.
 */
@Configuration
public class BasesDeDatosConfig {

    @Bean
    public DataSource dataSource(SelectorBase selector, Environment env) {
        String usuario = env.getRequiredProperty("app.bases.username");
        String password = env.getRequiredProperty("app.bases.password");
        int poolMaximo = env.getProperty("app.bases.pool-maximo", Integer.class, 5);

        Map<Object, Object> destinos = new EnumMap<>(Tamano.class);
        for (Tamano tamano : Tamano.values()) {
            String url = env.getRequiredProperty("app.bases." + tamano.name().toLowerCase() + ".url");

            HikariDataSource ds = new HikariDataSource();
            ds.setPoolName("pool-" + tamano);
            ds.setJdbcUrl(url);
            ds.setUsername(usuario);
            ds.setPassword(password);
            // Default de Hikari = 10; con 3 pools serian 30 conexiones fijas
            ds.setMaximumPoolSize(poolMaximo);

            Flyway.configure()
                    .dataSource(ds)
                    .locations("classpath:db/migration")
                    .validateMigrationNaming(true)
                    .load()
                    .migrate();

            destinos.put(tamano, ds);
        }

        BaseEnrutada ruteo = new BaseEnrutada(selector);
        ruteo.setTargetDataSources(destinos);
        // Sin default a proposito: una llave desconocida debe fallar fuerte, no caer en otra base
        ruteo.setLenientFallback(false);
        return ruteo;
    }
}
