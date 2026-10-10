package mx.Tienda_inteligente.pls.RecordsDeDatos;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record SolicitudPrediccion(
        List<Long> productoIds,                           // Cuáles productos predecir
        Map<Long, List<VentaDia>> historialVentas,       // El histórico de cada producto
        Map<Long, ProductoInfo> productosInfo,            // Atributos de cada producto
        Map<LocalDate, ContextoDia> contextoPorFecha,    // Qué sucede cada día
        LocalDate fechaBase,                              // Hoy (para backtesting)
        int diasAdelante,                                 // Cuántos días predecir
        boolean iaHabilitada                              // Usar LLM para ajustar
) {}