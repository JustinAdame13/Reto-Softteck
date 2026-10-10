package mx.Tienda_inteligente.pls.RecordsDeDatos;

import mx.Tienda_inteligente.pls.Enums.Enums;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.math.BigDecimal;

public record ResultadoPrediccion(
        Long productoId,
        LocalDate fechaObjetivo,
        BigDecimal cantidadPredicha,      // Lo que el algoritmo dice que se venderá
        BigDecimal limiteInferior,        // Intervalo de confianza (90%)
        BigDecimal limiteSuperior,
        int stockRecomendado,             // Punto de reorden + seguridad
        int cantidadAPedir,               // stockRec - stockActual
        Enums.ModeloPrediccion modelo,    // BASE o IA (lo que se usó)
        LocalDateTime generadaEn,
        DesgloseFactores desglose,        // Para auditoría y explicabilidad
        String explicacion                // Mensaje opcional del LLM si IA
) {}