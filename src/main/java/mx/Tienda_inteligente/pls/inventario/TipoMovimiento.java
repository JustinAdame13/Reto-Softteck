package mx.Tienda_inteligente.pls.inventario;

/** ENTRADA y DEVOLUCION suman; MERMA y CADUCIDAD restan; AJUSTE acepta positivo o negativo. */
public enum TipoMovimiento {
    ENTRADA(1), MERMA(-1), CADUCIDAD(-1), AJUSTE(1), DEVOLUCION(1);

    private final int signo;

    TipoMovimiento(int signo) {
        this.signo = signo;
    }

    public int delta(int cantidad) {
        return this == AJUSTE ? cantidad : signo * Math.abs(cantidad);
    }
}
