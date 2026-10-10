package mx.Tienda_inteligente.pls.RecordsDeDatos;

import mx.Tienda_inteligente.pls.Enums.Enums;

import java.time.LocalDate;
// Contexto del día (festivo, quincena, temporada)

public record ContextoDia(
        LocalDate fecha,
        boolean esFestivo,
        boolean esQuincena,       // Días 1-15 y 16-último del mes
        Enums.Temporada temporada,
        String descripcion        // "Fin de año", "Regreso a escuelas", etc.
) {}