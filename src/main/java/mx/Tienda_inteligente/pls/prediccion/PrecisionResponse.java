package mx.Tienda_inteligente.pls.prediccion;

import java.time.LocalDate;
import java.util.List;

/**
 * Error del modelo contra ventas reales (solo fechas ya pasadas).
 * mape: error porcentual promedio en dias con venta. wape: error total / ventas totales (mas robusto).
 * precision = 100 - wape. sesgo > 0 significa que el modelo sobreestima.
 */
public record PrecisionResponse(LocalDate desde, LocalDate hasta, List<PorModelo> modelos) {

    public record PorModelo(ModeloPrediccion modelo, int puntos, double mape, double wape, double sesgo,
                            double precision, List<PorProducto> porProducto) {}

    public record PorProducto(Long productoId, String sku, String nombre, int puntos, double real,
                              double predicho, double mape, double wape) {}
}
