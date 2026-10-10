package mx.Tienda_inteligente.pls.Interfaces;


import mx.Tienda_inteligente.pls.RecordsDeDatos.*;
import java.util.List;

public interface MotorPrediccion {
    List<ResultadoPrediccion> predecir(SolicitudPrediccion solicitud);
}