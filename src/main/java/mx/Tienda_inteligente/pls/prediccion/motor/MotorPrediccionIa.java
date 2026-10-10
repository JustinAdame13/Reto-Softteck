package mx.Tienda_inteligente.pls.prediccion.motor;

import mx.Tienda_inteligente.pls.contexto.ContextoDto;
import mx.Tienda_inteligente.pls.ia.ClienteIa;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Potencia el pronostico BASE con IA. No reemplaza al algoritmo: recibe su resultado, el historial
 * resumido y el calendario, y ajusta los numeros (por ejemplo, un evento que el algoritmo no tiene
 * suficiente historia para medir). Si la IA falla o no esta configurada, regresa Optional.empty().
 */
@Component
public class MotorPrediccionIa {

    private static final int PRODUCTOS_POR_LLAMADA = 12;

    private static final String SISTEMA = """
            Eres un analista de demanda para una tienda de retail en Mexico.
            Recibes, por producto, el historial reciente de ventas diarias y un pronostico estadistico (BASE)
            con su intervalo, ademas del calendario (quincenas, festivos, eventos, temporada, clima).
            Ajusta el pronostico solo cuando tengas una razon concreta (evento, temporada, tendencia, quiebre).
            Responde UNICAMENTE con un arreglo JSON, sin texto adicional, con un objeto por producto y dia:
            [{"productoId": 1, "fecha": "2026-10-10", "cantidad": 12.5, "min": 9.0, "max": 16.0}]
            Las cantidades son unidades por dia, nunca negativas, y min <= cantidad <= max.
            """;

    private final ClienteIa clienteIa;

    public MotorPrediccionIa(ClienteIa clienteIa) {
        this.clienteIa = clienteIa;
    }

    public boolean disponible() {
        return clienteIa.disponible();
    }

    public Optional<List<ResultadoPrediccion>> potenciar(EntradaPrediccion entrada, List<ResultadoPrediccion> base) {
        if (!clienteIa.disponible()) return Optional.empty();

        Map<Long, List<ResultadoPrediccion>> basePorProducto = base.stream()
                .collect(Collectors.groupingBy(ResultadoPrediccion::productoId));
        String calendario = calendario(entrada);

        List<ResultadoPrediccion> resultado = new ArrayList<>();
        List<ProductoHistorial> productos = entrada.productos();
        for (int i = 0; i < productos.size(); i += PRODUCTOS_POR_LLAMADA) {
            List<ProductoHistorial> bloque = productos.subList(i, Math.min(productos.size(), i + PRODUCTOS_POR_LLAMADA));
            StringBuilder prompt = new StringBuilder(calendario).append("\nPRODUCTOS:\n");
            for (ProductoHistorial p : bloque) {
                prompt.append(describir(p, basePorProducto.getOrDefault(p.productoId(), List.of())));
            }
            Optional<List<Object>> json = clienteIa.completarJsonArreglo(SISTEMA, prompt.toString(), 4000);
            if (json.isEmpty()) return Optional.empty();

            Map<String, ResultadoPrediccion> ajustes = new HashMap<>();
            for (Object o : json.get()) {
                if (!(o instanceof Map<?, ?> m)) continue;
                Double pid = ClienteIa.numero(m.get("productoId"));
                Double cant = ClienteIa.numero(m.get("cantidad"));
                Object fecha = m.get("fecha");
                if (pid == null || cant == null || fecha == null) continue;
                try {
                    LocalDate f = LocalDate.parse(fecha.toString());
                    double c = Math.max(0, cant);
                    Double min = ClienteIa.numero(m.get("min"));
                    Double max = ClienteIa.numero(m.get("max"));
                    double lo = min == null ? c * 0.8 : Math.max(0, Math.min(min, c));
                    double hi = max == null ? c * 1.2 : Math.max(max, c);
                    ajustes.put(pid.longValue() + "|" + f, new ResultadoPrediccion(pid.longValue(), f, r2(c), r2(lo), r2(hi)));
                } catch (Exception ignorada) {
                    // fila mal formada: se conserva el valor BASE
                }
            }
            // Si la IA omitio algun dia, se conserva el BASE para no dejar huecos
            for (ProductoHistorial p : bloque) {
                for (ResultadoPrediccion b : basePorProducto.getOrDefault(p.productoId(), List.of())) {
                    resultado.add(ajustes.getOrDefault(b.productoId() + "|" + b.fechaObjetivo(), b));
                }
            }
        }
        return Optional.of(resultado);
    }

    private String calendario(EntradaPrediccion e) {
        LocalDate fin = e.fechaInicio().plusDays(e.dias() - 1L);
        StringBuilder sb = new StringBuilder("CALENDARIO DE LOS DIAS A PREDECIR (fecha;diaSemana;quincena;evento;temporada;clima):\n");
        for (ContextoDto c : e.contexto()) {
            if (c.fecha().isBefore(e.fechaInicio()) || c.fecha().isAfter(fin)) continue;
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
            if (i < v.length - 1) sb.append(',');
        }
        sb.append("\n  semanas previas (total por semana, mas antiguo primero): ");
        List<Long> semanas = new ArrayList<>();
        for (int fin = desde; fin - 7 >= 0 && semanas.size() < 20; fin -= 7) {
            double s = 0;
            for (int i = fin - 7; i < fin; i++) s += v[i];
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
