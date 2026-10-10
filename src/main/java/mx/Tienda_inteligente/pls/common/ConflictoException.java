package mx.Tienda_inteligente.pls.common;

/** Se traduce a 409: duplicados, traslapes o transiciones de estado invalidas. */
public class ConflictoException extends RuntimeException {

    public ConflictoException(String mensaje) {
        super(mensaje);
    }
}
