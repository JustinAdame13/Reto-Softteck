package mx.Tienda_inteligente.pls.prediccion.motor;

import java.util.List;

/** Cualquier implementacion (Java, Python por HTTP, IA) cumple este contrato. */
public interface MotorPrediccion {

    List<ResultadoPrediccion> predecir(EntradaPrediccion entrada);
}
