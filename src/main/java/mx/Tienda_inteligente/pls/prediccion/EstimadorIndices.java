package mx.Tienda_inteligente.pls.prediccion;

import mx.Tienda_inteligente.pls.Enums.Enums;
import mx.Tienda_inteligente.pls.RecordsDeDatos.ContextoDia;
import mx.Tienda_inteligente.pls.RecordsDeDatos.IndicesContexto;
import mx.Tienda_inteligente.pls.RecordsDeDatos.VentaDia;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EstimadorIndices   {

        /** Mínimo de días normales para poder calcular una base confiable. */
        private static final int MINIMO_DIAS_BASE = 10;

        /**
         * Fuerza del shrinkage. Con K = 4, un índice calculado sobre
         * 4 muestras pesa la mitad y la otra mitad se jala hacia 1.0.
         */
        private static final double K_SHRINKAGE = 4.0;

        private static final double INDICE_MINIMO = 0.40;
        private static final double INDICE_MAXIMO = 2.50;

        public IndicesContexto estimar(
                List<VentaDia> historial,
                Map<LocalDate, ContextoDia> contexto) {

            // Solo días limpios: sin promoción y sin quiebre de stock.
            // Un día con 30% de descuento no dice nada sobre el efecto
            // del festivo, dice sobre el efecto del descuento.
            List<VentaDia> limpios = historial.stream()
                    .filter(v -> !v.tuvoPromocion() && !v.huboQuiebreStock())
                    .toList();

            if (limpios.isEmpty()) {
                return IndicesContexto.neutros();
            }

            // Clasificar cada día limpio
            List<Integer> normales = new ArrayList<>();
            List<Integer> festivos = new ArrayList<>();
            List<Integer> quincenas = new ArrayList<>();
            Map<Enums.Temporada, List<Integer>> porTemporada = new HashMap<>();

            for (VentaDia v : limpios) {
                ContextoDia ctx = contexto.get(v.fecha());
                int u = v.unidades();

                if (ctx == null) {
                    normales.add(u);
                    continue;
                }

                if (ctx.esFestivo()) festivos.add(u);
                if (ctx.esQuincena()) quincenas.add(u);

                if (ctx.temporada() != null
                        && ctx.temporada() != Enums.Temporada.NORMAL) {
                    porTemporada
                            .computeIfAbsent(ctx.temporada(), t -> new ArrayList<>())
                            .add(u);
                }

                // La base son los días sin ninguna condición especial
                boolean esNormal = !ctx.esFestivo()
                        && !ctx.esQuincena()
                        && (ctx.temporada() == null
                        || ctx.temporada() == Enums.Temporada.NORMAL);
                if (esNormal) normales.add(u);
            }

            // Sin suficientes días normales no hay con qué comparar
            if (normales.size() < MINIMO_DIAS_BASE) {
                return IndicesContexto.neutros();
            }

            double base = promedio(normales);
            if (base <= 0) {
                return IndicesContexto.neutros();
            }

            double idxFestivo = indiceCon(festivos, base);
            double idxQuincena = indiceCon(quincenas, base);

            Map<Enums.Temporada, Double> idxTemporadas = new HashMap<>();
            for (var e : porTemporada.entrySet()) {
                idxTemporadas.put(e.getKey(), indiceCon(e.getValue(), base));
            }

            return new IndicesContexto(
                    idxFestivo,
                    idxQuincena,
                    idxTemporadas,
                    redondear(base),
                    festivos.size(),
                    quincenas.size()
            );
        }

        /**
         * Calcula el índice y lo jala hacia 1.0 según cuántas muestras
         * lo respalden. Pocas muestras, poca confianza, índice tímido.
         */
        private double indiceCon(List<Integer> muestras, double base) {
            if (muestras.isEmpty()) return 1.0;

            double crudo = promedio(muestras) / base;
            crudo = Math.max(INDICE_MINIMO, Math.min(INDICE_MAXIMO, crudo));

            int n = muestras.size();
            double peso = n / (n + K_SHRINKAGE);

            double ajustado = peso * crudo + (1 - peso) * 1.0;
            return redondear(ajustado);
        }

        private double promedio(List<Integer> xs) {
            return xs.stream().mapToInt(Integer::intValue).average().orElse(0);
        }

        private double redondear(double v) {
            return Math.round(v * 1000.0) / 1000.0;
        }
}
