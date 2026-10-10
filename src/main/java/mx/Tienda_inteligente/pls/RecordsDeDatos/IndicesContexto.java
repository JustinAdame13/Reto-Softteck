package mx.Tienda_inteligente.pls.RecordsDeDatos;

import mx.Tienda_inteligente.pls.Enums.Enums;

import java.util.Map;

public record IndicesContexto(
        double indiceFestivo,
        double indiceQuincena,
        Map<Enums.Temporada, Double> indicesTemporada,
        double ventaBaseDiaria,
        int muestrasFestivo,
        int muestrasQuincena
) {
    public static IndicesContexto neutros() {
        return new IndicesContexto(1.0, 1.0, Map.of(), 0.0, 0, 0);
    }

    public double indiceDe(Enums.Temporada temporada) {
        if (temporada == null) return 1.0;
        return indicesTemporada.getOrDefault(temporada, 1.0);
    }
}