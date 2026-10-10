package mx.Tienda_inteligente.pls.inventario;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ReabastoItem(
        Long productoId,
        String sku,
        String nombre,
        String categoria,
        int stockActual,
        int stockSeguridad,
        double demandaHorizonte,
        int horizonteDias,
        int cantidadAPedir,
        BigDecimal costoEstimado,
        LocalDate pedirAntesDe,
        String fuente,
        String prioridad
) {
}
