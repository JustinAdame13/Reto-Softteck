package mx.Tienda_inteligente.pls.inventario;

public enum Semaforo {
    /** Sin existencias. */
    AGOTADO,
    /** Por debajo del stock de seguridad. */
    CRITICO,
    /** Por debajo del punto de reorden: hay que pedir. */
    BAJO,
    OK,
    /** Demasiado inventario (dinero detenido o riesgo de caducidad). */
    SOBRESTOCK
}
