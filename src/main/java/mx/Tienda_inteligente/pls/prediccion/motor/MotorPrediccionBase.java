package mx.Tienda_inteligente.pls.prediccion.motor;

import mx.Tienda_inteligente.pls.contexto.CalendarioMexico;
import mx.Tienda_inteligente.pls.contexto.ContextoDto;
import mx.Tienda_inteligente.pls.contexto.Temporada;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Algoritmo BASE (sin IA): descomposicion multiplicativa + suavizamiento exponencial.
 *
 * <pre>
 * prediccion(dia) = media x nivel x tendencia^h x f_diaSemana x f_quincena x f_evento x f_temporada
 * </pre>
 *
 * <ul>
 *   <li><b>f_diaSemana</b>: cuanto vende cada dia de la semana contra el promedio (los sabados no son martes).</li>
 *   <li><b>f_quincena</b>: efecto de los dias de pago (15 y fin de mes), muy fuerte en Mexico.</li>
 *   <li><b>f_evento</b>: festivos y eventos comerciales (Buen Fin, 14 de febrero, Dia de las Madres...).</li>
 *   <li><b>f_temporada</b>: Navidad, verano, regreso a clases.</li>
 *   <li><b>nivel</b>: suavizamiento exponencial de la serie ya sin estacionalidad (se adapta a cambios recientes).</li>
 *   <li><b>tendencia</b>: crecimiento diario comparando las ultimas 4 semanas contra las 4 anteriores (acotado).</li>
 * </ul>
 * Cada factor se "encoge" hacia 1 cuando hay pocos datos (shrinkage) para no sobreajustar.
 * El intervalo (80 %) sale de la desviacion de los errores de pronostico a un paso.
 * Productos sin historial usan el patron promedio de su categoria.
 */
@Component
public class MotorPrediccionBase implements MotorPrediccion {

    private static final double SHRINK = 3.0;
    private static final double ALFA = 0.15;
    private static final double Z80 = 1.2816;

    @Override
    public List<ResultadoPrediccion> predecir(EntradaPrediccion entrada) {
        Map<LocalDate, ContextoDto> ctx = new HashMap<>();
        if (entrada.contexto() != null) {
            entrada.contexto().forEach(c -> ctx.put(c.fecha(), c));
        }

        // Serie promedio por categoria para productos nuevos (sin ventas)
        Map<String, double[]> sumaCategoria = new HashMap<>();
        Map<String, Integer> conteoCategoria = new HashMap<>();
        Map<String, LocalDate> desdeCategoria = new HashMap<>();
        for (ProductoHistorial p : entrada.productos()) {
            if (total(p.ventasDiarias()) > 0) {
                double[] acc = sumaCategoria.computeIfAbsent(p.categoria(), k -> new double[p.ventasDiarias().length]);
                if (acc.length == p.ventasDiarias().length) {
                    for (int i = 0; i < acc.length; i++) acc[i] += p.ventasDiarias()[i];
                    conteoCategoria.merge(p.categoria(), 1, Integer::sum);
                    desdeCategoria.putIfAbsent(p.categoria(), p.historialDesde());
                }
            }
        }

        List<ResultadoPrediccion> resultado = new ArrayList<>();
        for (ProductoHistorial p : entrada.productos()) {
            double[] y = p.ventasDiarias();
            LocalDate desde = p.historialDesde();
            if (total(y) == 0 && sumaCategoria.containsKey(p.categoria())) {
                double[] acc = sumaCategoria.get(p.categoria());
                int n = conteoCategoria.get(p.categoria());
                y = new double[acc.length];
                for (int i = 0; i < acc.length; i++) y[i] = acc[i] / n;
                desde = desdeCategoria.get(p.categoria());
            }
            resultado.addAll(predecirSerie(p.productoId(), y, desde, entrada.fechaInicio(), entrada.dias(), ctx));
        }
        return resultado;
    }

    List<ResultadoPrediccion> predecirSerie(Long productoId, double[] y, LocalDate desde, LocalDate inicio,
                                            int dias, Map<LocalDate, ContextoDto> ctx) {
        List<ResultadoPrediccion> r = new ArrayList<>(dias);
        int n = y == null ? 0 : y.length;
        double media = n == 0 ? 0 : total(y) / n;
        if (media <= 0) {
            for (int h = 0; h < dias; h++) r.add(new ResultadoPrediccion(productoId, inicio.plusDays(h), 0, 0, 0));
            return r;
        }

        ContextoDto[] c = new ContextoDto[n];
        for (int i = 0; i < n; i++) c[i] = contexto(ctx, desde.plusDays(i));

        // 1) Dia de la semana
        double[] sumaDow = new double[8];
        int[] nDow = new int[8];
        for (int i = 0; i < n; i++) {
            int d = desde.plusDays(i).getDayOfWeek().getValue();
            sumaDow[d] += y[i];
            nDow[d]++;
        }
        double[] fDow = new double[8];
        for (int d = 1; d <= 7; d++) {
            fDow[d] = (sumaDow[d] + SHRINK * media) / ((nDow[d] + SHRINK) * media);
        }

        // Serie relativa sin efecto de dia de semana
        double[] rel = new double[n];
        for (int i = 0; i < n; i++) rel[i] = y[i] / (media * fDow[desde.plusDays(i).getDayOfWeek().getValue()]);

        // 2) Quincena y evento: promedio relativo con la marca vs sin la marca
        double fQuin = factorBinario(rel, i -> Boolean.TRUE.equals(c[i].esQuincena()));
        double fEvento = factorBinario(rel, i -> c[i].tieneEvento());

        // 3) Temporada contra NORMAL
        Map<Temporada, double[]> acumTemp = new EnumMap<>(Temporada.class);
        for (int i = 0; i < n; i++) {
            Temporada t = c[i].temporada() == null ? Temporada.NORMAL : c[i].temporada();
            double[] a = acumTemp.computeIfAbsent(t, k -> new double[2]);
            a[0] += rel[i];
            a[1]++;
        }
        double[] normal = acumTemp.getOrDefault(Temporada.NORMAL, new double[]{0, 0});
        double baseNormal = (normal[0] + SHRINK) / (normal[1] + SHRINK);
        Map<Temporada, Double> fTemp = new EnumMap<>(Temporada.class);
        for (Temporada t : Temporada.values()) {
            double[] a = acumTemp.getOrDefault(t, new double[]{0, 0});
            fTemp.put(t, t == Temporada.NORMAL ? 1.0 : acotar(((a[0] + SHRINK) / (a[1] + SHRINK)) / baseNormal, 0.5, 2.5));
        }

        // 4) Serie desestacionalizada y suavizamiento exponencial del nivel con errores a un paso
        double nivel = 0;
        int arranque = Math.min(7, n);
        double[] z = new double[n];
        for (int i = 0; i < n; i++) {
            z[i] = y[i] / (media * factores(desde.plusDays(i), c[i], fDow, fQuin, fEvento, fTemp));
        }
        for (int i = 0; i < arranque; i++) nivel += z[i];
        nivel /= arranque;
        double sumaErr2 = 0;
        int nErr = 0;
        int inicioErr = Math.max(arranque, n - 56);
        for (int i = arranque; i < n; i++) {
            double ajuste = media * nivel * factores(desde.plusDays(i), c[i], fDow, fQuin, fEvento, fTemp);
            if (i >= inicioErr) {
                sumaErr2 += Math.pow(y[i] - ajuste, 2);
                nErr++;
            }
            nivel = ALFA * z[i] + (1 - ALFA) * nivel;
        }
        double sigma = nErr > 1 ? Math.sqrt(sumaErr2 / (nErr - 1)) : Math.sqrt(media);

        // 5) Tendencia: ultimas 4 semanas vs las 4 anteriores, acotada a +-1 % diario
        double tendencia = 1.0;
        if (n >= 56) {
            double reciente = promedio(z, n - 28, n);
            double previa = promedio(z, n - 56, n - 28);
            if (previa > 0 && reciente > 0) {
                tendencia = acotar(Math.pow(reciente / previa, 1.0 / 28), 0.99, 1.01);
            }
        }

        // 6) Pronostico
        for (int h = 0; h < dias; h++) {
            LocalDate f = inicio.plusDays(h);
            ContextoDto cf = contexto(ctx, f);
            double fac = factores(f, cf, fDow, fQuin, fEvento, fTemp);
            double pred = Math.max(0, media * nivel * Math.pow(tendencia, h + 1) * fac);
            double banda = Z80 * sigma * Math.sqrt(fac) * Math.sqrt(1 + 0.03 * h);
            r.add(new ResultadoPrediccion(productoId, f, r2(pred), r2(Math.max(0, pred - banda)), r2(pred + banda)));
        }
        return r;
    }

    private static double factores(LocalDate f, ContextoDto c, double[] fDow, double fQuin, double fEvento,
                                   Map<Temporada, Double> fTemp) {
        double v = fDow[f.getDayOfWeek().getValue()];
        if (Boolean.TRUE.equals(c.esQuincena())) v *= fQuin;
        if (c.tieneEvento()) v *= fEvento;
        v *= fTemp.getOrDefault(c.temporada() == null ? Temporada.NORMAL : c.temporada(), 1.0);
        return Math.max(v, 0.05);
    }

    private interface Marca {
        boolean en(int i);
    }

    private static double factorBinario(double[] rel, Marca marca) {
        double sCon = 0, nCon = 0, sSin = 0, nSin = 0;
        for (int i = 0; i < rel.length; i++) {
            if (marca.en(i)) {
                sCon += rel[i];
                nCon++;
            } else {
                sSin += rel[i];
                nSin++;
            }
        }
        double con = (sCon + SHRINK) / (nCon + SHRINK);
        double sin = (sSin + SHRINK) / (nSin + SHRINK);
        return acotar(con / sin, 0.5, 3.0);
    }

    private static ContextoDto contexto(Map<LocalDate, ContextoDto> ctx, LocalDate f) {
        ContextoDto c = ctx.get(f);
        return c != null ? c : CalendarioMexico.calcular(f);
    }

    private static double total(double[] v) {
        double s = 0;
        if (v != null) for (double x : v) s += x;
        return s;
    }

    private static double promedio(double[] v, int desde, int hasta) {
        double s = 0;
        for (int i = desde; i < hasta; i++) s += v[i];
        return s / Math.max(1, hasta - desde);
    }

    private static double acotar(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    private static double r2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
