package mx.Tienda_inteligente.pls.prediccion.motor;

import mx.Tienda_inteligente.pls.contexto.ContextoDto;
import mx.Tienda_inteligente.pls.ia.ClienteIa;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Potencia el pronostico BASE con IA. No lo reemplaza: recibe su resultado, el
 * historial resumido y el calendario, y ajusta los numeros (por ejemplo, un evento
 * que el algoritmo no tiene suficiente historia para medir).
 *
 * Tres reglas de contencion, porque la salida de un LLM no es de fiar:
 *
 * 1. BANDA. El ajuste nunca puede salirse de [BASE x 0.5, BASE x 2]. Si el modelo
 *    dice 500 donde el BASE decia 12, se recorta a 24 y se avisa. Sin esto, un
 *    numero alucinado entra a planificar() y sale como una orden de compra real.
 *    Cuando el BASE es 0 la banda colapsa, asi que ahi el techo es el maximo
 *    historico del producto: la IA puede levantar un cero, pero no inventar demanda
 *    que el producto nunca ha tenido.
 * 2. DEGRADADO POR LOTE. Si un lote falla, ese lote se queda en BASE y los demas
 *    siguen. Antes un solo error descartaba la prediccion completa.
 * 3. SIN HUECOS. Si la IA omite un producto o un dia, se conserva el valor BASE.
 */
@Component
public class MotorPrediccionIa {

    private static final int PRODUCTOS_POR_LLAMADA = 10;
    private static final int MAX_TOKENS = 8000;

    /** Cuanto puede alejarse el ajuste del BASE, hacia abajo y hacia arriba. */
    private static final double AJUSTE_MINIMO = 0.5;
    private static final double AJUSTE_MAXIMO = 2.0;

    private static final String SISTEMA = """
            Eres un analista de demanda para una tienda de retail en Mexico.
            Recibes, por producto, el historial reciente de ventas diarias y un pronostico estadistico (BASE)
            con su intervalo, ademas del calendario (quincenas, festivos, eventos, temporada, clima).
            Ajusta el pronostico solo cuando tengas una razon concreta (evento, temporada, tendencia, quiebre).
            Si no tienes una razon clara, repite el valor BASE.
            Predice la demanda SIN promocion: el descuento lo aplica despues otro motor, asi que si
            incluyes el efecto de una promo el sistema lo contaria dos veces.
            Responde UNICAMENTE con un arreglo JSON, sin texto adicional, con un objeto por producto:
            [{"productoId": 1,
              "explicacion": "una frase corta con la razon del ajuste",
              "dias": [{"fecha": "2026-10-10", "cantidad": 12.5, "min": 9.0, "max": 16.0}]}]
            Las cantidades son unidades por dia, nunca negativas, y min <= cantidad <= max.
            Incluye todos los dias que te pasaron en el pronostico BASE de ese producto.
            """;

    private final ClienteIa clienteIa;

    public MotorPrediccionIa(ClienteIa clienteIa) {
        this.clienteIa = clienteIa;
    }

    public boolean disponible() {
        return clienteIa.disponible();
    }

    /**
     * @return Optional.empty() solo si la IA esta apagada o sin llave. Si esta
     *         prendida siempre regresa un resultado completo, aunque algunos lotes
     *         hayan fallado (esos se quedan en BASE y se reportan en advertencias).
     */
    public Optional<AjusteIa> potenciar(EntradaPrediccion entrada, List<ResultadoPrediccion> base) {
        if (!clienteIa.disponible()) {
            return Optional.empty();
        }

        Map<Long, List<ResultadoPrediccion>> basePorProducto = base.stream()
                .collect(Collectors.groupingBy(ResultadoPrediccion::productoId));
        String calendario = calendario(entrada);

        List<ResultadoPrediccion> resultado = new ArrayList<>();
        Map<Long, String> explicaciones = new LinkedHashMap<>();
        List<String> advertencias = new ArrayList<>();
        int lotesFallidos = 0;
        int recortados = 0;

        List<ProductoHistorial> productos = entrada.productos();
        for (int i = 0; i < productos.size(); i += PRODUCTOS_POR_LLAMADA) {
            List<ProductoHistorial> bloque = productos.subList(i, Math.min(productos.size(), i + PRODUCTOS_POR_LLAMADA));

            StringBuilder prompt = new StringBuilder(calendario).append("\nPRODUCTOS:\n");
            for (ProductoHistorial p : bloque) {
                prompt.append(describir(p, basePorProducto.getOrDefault(p.productoId(), List.of())));
            }

            Optional<List<Object>> json = clienteIa.completarJsonArreglo(SISTEMA, prompt.toString(), MAX_TOKENS);

            if (json.isEmpty()) {
                // Regla 2: este lote se queda en BASE, los demas siguen.
                lotesFallidos++;
                conservarBase(resultado, bloque, basePorProducto);
                continue;
            }

            Map<String, double[]> ajustes = leerAjustes(json.get(), explicaciones);
            recortados += fusionar(resultado, bloque, basePorProducto, ajustes);
        }

        if (lotesFallidos > 0) {
            advertencias.add("La IA no respondio en " + lotesFallidos
                    + " lote(s) de productos; esos se quedaron con el modelo BASE");
        }
        if (recortados > 0) {
            advertencias.add(recortados + " valor(es) de la IA se salieron de la banda permitida ("
                    + (int) (AJUSTE_MINIMO * 100) + "%-" + (int) (AJUSTE_MAXIMO * 100)
                    + "% del BASE) y se recortaron");
        }
        return Optional.of(new AjusteIa(resultado, explicaciones, advertencias));
    }

    /** Lee el JSON de la IA a un indice "productoId|fecha" -> {cantidad, min, max}. */
    private Map<String, double[]> leerAjustes(List<Object> filas, Map<Long, String> explicaciones) {
        Map<String, double[]> ajustes = new HashMap<>();
        for (Object fila : filas) {
            if (!(fila instanceof Map<?, ?> porProducto)) {
                continue;
            }
            Double pid = ClienteIa.numero(porProducto.get("productoId"));
            if (pid == null) {
                continue;
            }
            long productoId = pid.longValue();

            Object explicacion = porProducto.get("explicacion");
            if (explicacion != null && !explicacion.toString().isBlank()) {
                explicaciones.putIfAbsent(productoId, explicacion.toString().trim());
            }

            if (!(porProducto.get("dias") instanceof List<?> dias)) {
                continue;
            }
            for (Object d : dias) {
                if (!(d instanceof Map<?, ?> dia)) {
                    continue;
                }
                Double cantidad = ClienteIa.numero(dia.get("cantidad"));
                Object fecha = dia.get("fecha");
                if (cantidad == null || fecha == null) {
                    continue;
                }
                try {
                    LocalDate f = LocalDate.parse(fecha.toString().trim());
                    Double min = ClienteIa.numero(dia.get("min"));
                    Double max = ClienteIa.numero(dia.get("max"));
                    ajustes.put(clave(productoId, f), new double[]{
                            cantidad,
                            min == null ? Double.NaN : min,
                            max == null ? Double.NaN : max});
                } catch (Exception filaMalFormada) {
                    // se conserva el valor BASE de ese dia
                }
            }
        }
        return ajustes;
    }

    /**
     * Mezcla los ajustes con el BASE aplicando la banda.
     *
     * @return cuantos valores hubo que recortar.
     */
    private int fusionar(List<ResultadoPrediccion> resultado, List<ProductoHistorial> bloque,
                         Map<Long, List<ResultadoPrediccion>> basePorProducto, Map<String, double[]> ajustes) {
        int recortados = 0;
        for (ProductoHistorial p : bloque) {
            double maximoHistorico = maximoHistorico(p);
            for (ResultadoPrediccion b : basePorProducto.getOrDefault(p.productoId(), List.of())) {
                double[] a = ajustes.get(clave(b.productoId(), b.fechaObjetivo()));
                if (a == null) {
                    resultado.add(b);           // Regla 3: la IA omitio este dia
                    continue;
                }

                double piso = b.cantidadPredicha() * AJUSTE_MINIMO;
                double techo = b.cantidadPredicha() > 0
                        ? b.cantidadPredicha() * AJUSTE_MAXIMO
                        : Math.max(1.0, maximoHistorico);

                double cantidad = Math.max(0, a[0]);
                double acotada = Math.min(Math.max(cantidad, piso), techo);
                if (Math.abs(acotada - cantidad) > 0.01) {
                    recortados++;
                }

                double min = Double.isNaN(a[1]) ? acotada * 0.8 : a[1];
                double max = Double.isNaN(a[2]) ? acotada * 1.2 : a[2];
                double inferior = Math.max(0, Math.min(min, acotada));
                double superior = Math.max(max, acotada);

                resultado.add(new ResultadoPrediccion(b.productoId(), b.fechaObjetivo(),
                        r2(acotada), r2(inferior), r2(superior)));
            }
        }
        return recortados;
    }

    private void conservarBase(List<ResultadoPrediccion> resultado, List<ProductoHistorial> bloque,
                               Map<Long, List<ResultadoPrediccion>> basePorProducto) {
        for (ProductoHistorial p : bloque) {
            resultado.addAll(basePorProducto.getOrDefault(p.productoId(), List.of()));
        }
    }

    private static double maximoHistorico(ProductoHistorial p) {
        double max = 0;
        for (double v : p.ventasDiarias()) {
            max = Math.max(max, v);
        }
        return max;
    }

    private static String clave(long productoId, LocalDate fecha) {
        return productoId + "|" + fecha;
    }

    private String calendario(EntradaPrediccion e) {
        LocalDate fin = e.fechaInicio().plusDays(e.dias() - 1L);
        StringBuilder sb = new StringBuilder(
                "CALENDARIO DE LOS DIAS A PREDECIR (fecha;diaSemana;quincena;evento;temporada;clima):\n");
        for (ContextoDto c : e.contexto()) {
            if (c.fecha().isBefore(e.fechaInicio()) || c.fecha().isAfter(fin)) {
                continue;
            }
            sb.append(c.fecha()).append(';').append(c.fecha().getDayOfWeek()).append(';')
                    .append(Boolean.TRUE.equals(c.esQuincena()) ? "si" : "no").append(';')
                    .append(c.nombreEvento() == null ? "-" : c.nombreEvento()).append(';')
                    .append(c.temporada()).append(';')
                    .append(c.clima() == null ? "-" : c.clima() + (c.temperatura() == null ? "" : " " + c.temperatura() + "C"))
                    .append('\n');
        }
        return sb.toString();
    }

    private String describir(ProductoHistorial p, List<ResultadoPrediccion> base) {
        StringBuilder sb = new StringBuilder();
        sb.append("- productoId=").append(p.productoId()).append(" | ").append(p.nombre())
                .append(" | categoria=").append(p.categoria())
                .append(" | precio=").append(p.precioVenta())
                .append(p.esPerecedero() ? " | perecedero vida util " + p.vidaUtilDias() + " dias" : "")
                .append('\n');

        double[] v = p.ventasDiarias();
        int desde = Math.max(0, v.length - 56);
        sb.append("  ventas ultimos ").append(v.length - desde).append(" dias (mas antiguo primero): ");
        for (int i = desde; i < v.length; i++) {
            sb.append(Math.round(v[i]));
            if (i < v.length - 1) {
                sb.append(',');
            }
        }

        sb.append("\n  semanas previas (total por semana, mas antiguo primero): ");
        List<Long> semanas = new ArrayList<>();
        for (int fin = desde; fin - 7 >= 0 && semanas.size() < 20; fin -= 7) {
            double s = 0;
            for (int i = fin - 7; i < fin; i++) {
                s += v[i];
            }
            semanas.add(0, Math.round(s));
        }
        sb.append(semanas.isEmpty() ? "-" : semanas.stream().map(String::valueOf).collect(Collectors.joining(",")));

        sb.append("\n  pronostico BASE (fecha=cantidad[min-max]): ");
        for (ResultadoPrediccion b : base) {
            sb.append(b.fechaObjetivo()).append('=').append(b.cantidadPredicha())
                    .append('[').append(b.limiteInferior()).append('-').append(b.limiteSuperior()).append("] ");
        }
        return sb.append('\n').toString();
    }

    private static double r2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
