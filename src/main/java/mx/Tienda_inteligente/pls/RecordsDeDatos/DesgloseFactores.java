package mx.Tienda_inteligente.pls.RecordsDeDatos;

import java.math.BigDecimal;

public record DesgloseFactores(
        BigDecimal mediaMovilPonderada,
        double factorTendencia,           // 0.7 a 1.5
        double factorContexto,            // 1.0 a 3.0
        double factorAjusteIA             // 1.0 si es BASE, otro si es IA
) {}