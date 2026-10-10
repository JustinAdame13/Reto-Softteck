package mx.Tienda_inteligente.pls.RecordsDeDatos;

import java.math.BigDecimal;

// Información del producto
public record ProductoInfo(
        Long productoId,
        String nombre,
        int leadTimeDias,         // Cuántos días tarda en llegar
        int stockActual,
        int stockMinimo,
        BigDecimal precioActual,
        int rotacionDiasPromedio  // Cuánto vende en promedio por día
) {}