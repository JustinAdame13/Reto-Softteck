package mx.Tienda_inteligente.pls.prediccion;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.time.LocalDate;
import java.util.List;

/**
 * modelo: AUTO (respeta el switch de la configuracion), BASE, IA o AMBOS.
 * productoIds vacio = todos los productos activos.
 * fechaInicio (opcional, defecto hoy): si es una fecha pasada, el motor solo usa ventas ANTERIORES
 * a ella; sirve para hacer backtesting y mostrar /precision en la demo sin esperar dias.
 */
public record GenerarPrediccionRequest(
        @Min(1) @Max(60) Integer dias,
        List<Long> productoIds,
        String modelo,
        LocalDate fechaInicio
) {
}
