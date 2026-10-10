package mx.Tienda_inteligente.pls.common;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.util.ArrayList;
import java.util.List;

/** Arma clausulas WHERE dinamicas solo con los filtros que vienen (los null se ignoran). */
public class FiltroSql {

    private final List<String> condiciones = new ArrayList<>();
    private final MapSqlParameterSource params = new MapSqlParameterSource();

    public FiltroSql si(Object valor, String condicion, String nombre) {
        if (valor != null && !(valor instanceof String s && s.isBlank())) {
            condiciones.add(condicion);
            params.addValue(nombre, valor);
        }
        return this;
    }

    public FiltroSql siempre(String condicion) {
        condiciones.add(condicion);
        return this;
    }

    public FiltroSql param(String nombre, Object valor) {
        params.addValue(nombre, valor);
        return this;
    }

    public String where() {
        return condiciones.isEmpty() ? "" : " WHERE " + String.join(" AND ", condiciones);
    }

    public MapSqlParameterSource params() {
        return params;
    }
}
