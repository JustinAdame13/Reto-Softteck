package mx.Tienda_inteligente.pls.common;

import java.util.Map;

/** Se traduce a 422: los datos tienen buen formato pero violan una regla del negocio. */
public class ReglaNegocioException extends RuntimeException {

    private final Map<String, String> campos;

    public ReglaNegocioException(String mensaje) {
        this(mensaje, Map.of());
    }

    public ReglaNegocioException(String mensaje, Map<String, String> campos) {
        super(mensaje);
        this.campos = campos == null ? Map.of() : campos;
    }

    public Map<String, String> getCampos() {
        return campos;
    }
}
