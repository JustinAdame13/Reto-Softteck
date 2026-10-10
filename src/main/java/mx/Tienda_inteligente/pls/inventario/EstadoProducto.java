package mx.Tienda_inteligente.pls.inventario;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Fila de /inventario/estado. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EstadoProducto(
        Long productoId,
        String sku,
        String nombre,
        String categoria,
        int stockActual,
        int stockMinimo,
        Integer stockMaximo,
        int leadTimeDias,
        double demandaDiaria,
        double desviacionDiaria,
        int stockSeguridad,
        int puntoReorden,
        Double diasCobertura,
        int diasSinVenta,
        Semaforo semaforo,
        BigDecimal valorInventario,
        boolean esPerecedero,
        LocalDate caducidadEstimada,
        Integer diasParaCaducar,
        Integer unidadesEnRiesgo,
        BigDecimal costoUnitario,
        BigDecimal precioVenta
) {
}
