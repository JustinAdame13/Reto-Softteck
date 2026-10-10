package mx.Tienda_inteligente.pls.basedatos;

import mx.Tienda_inteligente.pls.configuracion.Tamano;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Guarda cual de las 3 bases (sucursal pequena, mediana o grande) esta elegida.
 *
 * - elegida: la que se escogio en el frontend. Global para todo el servidor.
 * - DE_ESTA_PETICION: la base "congelada" al inicio de cada peticion HTTP
 *   (la fija FiltroBase). Asi, si alguien cambia de base mientras una
 *   prediccion espera 90 s a la IA, esa prediccion sigue guardando en la
 *   base donde empezo y no en la nueva.
 */
@Component
public class SelectorBase {

    private final AtomicReference<Tamano> elegida;
    private static final ThreadLocal<Tamano> DE_ESTA_PETICION = new ThreadLocal<>();

    public SelectorBase(@Value("${app.bases.inicial:MEDIANA}") Tamano inicial) {
        this.elegida = new AtomicReference<>(inicial);
    }

    public Tamano elegida() {
        return elegida.get();
    }

    public void cambiar(Tamano tamano) {
        elegida.set(tamano);
    }

    /** La base con la que trabaja la peticion actual (o la elegida si no hay peticion, p. ej. al arrancar). */
    public Tamano actual() {
        Tamano t = DE_ESTA_PETICION.get();
        return t != null ? t : elegida.get();
    }

    void fijar(Tamano tamano) {
        DE_ESTA_PETICION.set(tamano);
    }

    void limpiar() {
        DE_ESTA_PETICION.remove();
    }
}
