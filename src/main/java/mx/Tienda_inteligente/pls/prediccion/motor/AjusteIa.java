package mx.Tienda_inteligente.pls.prediccion.motor;

import java.util.List;
import java.util.Map;

/**
 * Lo que regresa el motor de IA. Ademas de los numeros trae:
 *
 * - explicaciones: por que ajusto cada producto. Es el valor que un LLM agrega
 *   sobre un modelo estadistico, y es lo que se le ensena al jurado.
 * - advertencias: que lotes no respondieron y cuantos valores se recortaron por
 *   salirse de la banda permitida. Se devuelven al frontend en GeneracionResponse
 *   en lugar de quedarse solo en el log.
 */
public record AjusteIa(
        List<ResultadoPrediccion> predicciones,
        Map<Long, String> explicaciones,
        List<String> advertencias
) {
}
