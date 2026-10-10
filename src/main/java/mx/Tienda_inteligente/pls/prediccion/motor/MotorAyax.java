package mx.Tienda_inteligente.pls.prediccion.motor;

import mx.Tienda_inteligente.pls.contexto.CalendarioMexico;
import mx.Tienda_inteligente.pls.contexto.ContextoDto;
import mx.Tienda_inteligente.pls.contexto.Temporada;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * <h2>Ayax</h2>
 * Algoritmo de pronostico de demanda de la Tienda Inteligente. Funciona solo (sin IA);
 * {@link MotorAyaxIa} puede potenciarlo despues.
 *
 * <h3>1. Dos metodos segun el tipo de producto</h3>
 * <ul>
 *   <li><b>Ayax-Estacional</b> (productos de venta regular): descomposicion multiplicativa
 *   + suavizamiento exponencial.
 *   <pre>prediccion(dia) = media x nivel x tendencia^h x f_diaSemana x f_quincena x f_evento x f_temporada</pre>
 *   f_diaSemana (los sabados no son martes), f_quincena (dias de pago), f_evento (festivos, Buen Fin...),
 *   f_temporada (Navidad, verano, regreso a clases), nivel (se adapta a cambios recientes) y
 *   tendencia (ultimas 4 semanas vs las 4 anteriores, acotada a +-1 % diario).
 *   Cada factor se "encoge" hacia 1 cuando hay pocos datos (shrinkage).</li>
 *   <li><b>Ayax-Intermitente</b> (productos de baja rotacion, con muchos dias en cero): metodo de
 *   Croston con la correccion SBA. Suaviza por separado cuanto se vende cuando se vende y cada
 *   cuantos dias pasa. El metodo estacional sobreestima estos productos y deja stock de mas.</li>
 * </ul>
 *
 * <h3>2. Autocalibracion por producto</h3>
 * Para cada producto se prueban varias configuraciones (que tan rapido se adapta el nivel,
 * que tanto se encogen los factores, y Croston si el producto es intermitente). Se hace un
 * "examen" con datos que si conocemos: 3 veces se entrena hasta cierto dia y se le pide
 * pronosticar las 2 semanas siguientes (rolling origin). Gana la de menor puntaje = WAPE + |sesgo|:
 * que se equivoque poco dia a dia Y que no se cargue hacia un lado (solo WAPE premia pronosticar
 * de menos, y eso es quedarse sin stock). La configuracion de fabrica solo se reemplaza si la
 * mejora es de al menos 10 %, para no sobreajustar al ruido. Con la ganadora se entrena el modelo final.
 *
 * <h3>3. Solo aprende de dias limpios</h3>
 * Excluye dias con promocion (inflan la demanda y el motor de promociones agrega su efecto despues)
 * y rachas de 3+ dias en cero en productos que venden 3+ diarios (probable quiebre de stock).
 *
 * <p>El intervalo (80 %) sale de la desviacion de los errores de pronostico a un paso.
 * Productos sin historial usan el patron promedio de su categoria.
 */
@Component
public class MotorAyax implements MotorPrediccion {

    public static final String NOMBRE = "AYAX";

    private static final double Z80 = 1.2816;

    /** Configuracion por defecto (la que se usa si no hay historial para calibrar). */
    private static final double ALFA_DEFECTO = 0.15;
    private static final double SHRINK_DEFECTO = 3.0;

    /**
     * Rejilla de la autocalibracion: 3 x 2 = 6 configuraciones estacionales (+4 de Croston).
     * Se probo una rejilla mas grande (15) y sobreajustaba: elegia por ruido de la validacion.
     */
    private static final double[] ALFAS = {0.05, 0.15, 0.35};
    private static final double[] SHRINKS = {3.0, 10.0};
    /** Configuraciones de Croston (solo para productos intermitentes). */
    private static final double[] ALFAS_CROSTON = {0.05, 0.10, 0.20, 0.30};

    /** Validacion "rolling origin": 3 ventanas de 14 dias al final del historial. */
    private static final int VENTANAS_VALIDACION = 3;
    private static final int DIAS_POR_VENTANA = 14;
    /** La configuracion de fabrica solo se reemplaza si el puntaje mejora al menos 10 %. */
    private static final double MARGEN_MEJORA = 0.10;
    /** Minimo de dias de entrenamiento para que la validacion sea confiable. */
    private static final int MIN_ENTRENAMIENTO = 56;

    /** Intermitente: al menos esta fraccion de dias limpios en cero... */
    private static final double FRACCION_CEROS_INTERMITENTE = 0.30;
    /** ...y al menos esta cantidad de dias con venta para poder suavizar algo. */
    private static final int MIN_DIAS_CON_VENTA = 4;

    /** Minimo de dias limpios para confiar en ellos; con menos se usa todo el historial. */
    private static final int MIN_DIAS_LIMPIOS = 14;
    /** Quiebre probable: al menos esta racha de ceros... */
    private static final int RACHA_QUIEBRE = 3;
    /** ...en un producto que vende al menos esto por dia en promedio. */
    private static final double VENTA_MIN_QUIEBRE = 3.0;

    /** Resultado completo: las predicciones y como quedo calibrado cada producto. */
    public record Resultado(List<ResultadoPrediccion> predicciones, Map<Long, CalibracionAyax> calibracion) {
    }

    @Override
    public List<ResultadoPrediccion> predecir(EntradaPrediccion entrada) {
        return predecirConCalibracion(entrada).predicciones();
    }

    public Resultado predecirConCalibracion(EntradaPrediccion entrada) {
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
        Map<Long, CalibracionAyax> calibracion = new LinkedHashMap<>();
        for (ProductoHistorial p : entrada.productos()) {
            double[] y = p.ventasDiarias();
            LocalDate desde = p.historialDesde();
            boolean[] promo = new boolean[y == null ? 0 : y.length];
            for (int i = 0; i < promo.length; i++) promo[i] = p.conPromocion(i);
            boolean porCategoria = false;

            if (total(y) == 0 && sumaCategoria.containsKey(p.categoria())) {
                double[] acc = sumaCategoria.get(p.categoria());
                int n = conteoCategoria.get(p.categoria());
                y = new double[acc.length];
                for (int i = 0; i < acc.length; i++) y[i] = acc[i] / n;
                desde = desdeCategoria.get(p.categoria());
                promo = new boolean[y.length]; // el promedio de categoria no trae marcas propias
                porCategoria = true;
            }

            Pronostico pr = pronosticarProducto(y, promo, desde, entrada.fechaInicio(), entrada.dias(), ctx);
            for (int h = 0; h < entrada.dias(); h++) {
                resultado.add(new ResultadoPrediccion(p.productoId(), entrada.fechaInicio().plusDays(h),
                        r2(pr.pred[h]), r2(pr.inf[h]), r2(pr.sup[h])));
            }
            CalibracionAyax cal = pr.calibracion;
            if (porCategoria) {
                cal = new CalibracionAyax(cal.metodo() + " (patron de categoria)", cal.alfa(), cal.shrink(),
                        cal.configuracionesProbadas(), cal.errorValidacion(), cal.sesgoValidacion(),
                        cal.errorConfiguracionDefecto());
            }
            calibracion.put(p.productoId(), cal);
        }
        return new Resultado(resultado, calibracion);
    }

    // =====================================================================
    // Autocalibracion
    // =====================================================================

    private Pronostico pronosticarProducto(double[] y, boolean[] promo, LocalDate desde, LocalDate inicio,
                                           int dias, Map<LocalDate, ContextoDto> ctx) {
        int n = y == null ? 0 : y.length;
        if (n == 0 || total(y) <= 0) {
            return Pronostico.ceros(dias, CalibracionAyax.sinHistorial());
        }
        boolean[] usar = diasLimpios(y, promo);
        boolean intermitente = esIntermitente(y, usar);

        List<Config> candidatos = new ArrayList<>();
        for (double a : ALFAS) for (double s : SHRINKS) candidatos.add(Config.estacional(a, s));
        if (intermitente) for (double a : ALFAS_CROSTON) candidatos.add(Config.croston(a));
        Config defecto = intermitente && fraccionCeros(y, usar) >= 0.5
                ? Config.croston(0.10)
                : Config.estacional(ALFA_DEFECTO, SHRINK_DEFECTO);

        Config elegida = defecto;
        Double errorElegida = null;
        Double sesgoElegida = null;
        Double errorDefecto = null;
        int probadas = 0;

        // Origenes de la validacion: varias ventanas para que una semana rara no decida sola
        List<Integer> origenes = new ArrayList<>();
        for (int k = VENTANAS_VALIDACION; k >= 1; k--) {
            int origen = n - k * DIAS_POR_VENTANA;
            if (origen >= MIN_ENTRENAMIENTO && contar(usar, origen, origen + DIAS_POR_VENTANA) >= 5) {
                origenes.add(origen);
            }
        }

        if (!origenes.isEmpty()) {
            Map<Config, double[]> resultados = new LinkedHashMap<>();
            for (Config cfg : candidatos) {
                double[] acum = new double[3]; // sumAbs, sumReal, sumPred
                boolean valido = true;
                for (int origen : origenes) {
                    Pronostico val = ajustar(cfg, Arrays.copyOf(y, origen), Arrays.copyOf(usar, origen),
                            desde, desde.plusDays(origen), DIAS_POR_VENTANA, ctx);
                    if (val == null) {
                        valido = false;
                        break;
                    }
                    acumular(acum, y, usar, origen, val.pred);
                }
                if (valido) {
                    resultados.put(cfg, metricas(acum));
                    probadas++;
                }
            }

            double[] mDef = resultados.get(defecto);
            double puntajeDef = mDef == null ? Double.MAX_VALUE : puntaje(mDef);
            Config mejor = defecto;
            double puntajeMejor = puntajeDef;
            for (var e : resultados.entrySet()) {
                double pt = puntaje(e.getValue());
                if (pt < puntajeMejor) {
                    puntajeMejor = pt;
                    mejor = e.getKey();
                }
            }
            // Solo se cambia la configuracion de fabrica si la mejora es clara (evita sobreajustar al ruido)
            if (mDef == null || puntajeMejor < puntajeDef * (1 - MARGEN_MEJORA)) {
                elegida = mejor;
            }
            double[] mEleg = resultados.get(elegida);
            if (mEleg != null) {
                errorElegida = r2(mEleg[0]);
                sesgoElegida = r2(mEleg[1]);
            }
            if (mDef != null) errorDefecto = r2(mDef[0]);
        }

        // Modelo final: la configuracion ganadora entrenada con TODO el historial
        Pronostico p = ajustar(elegida, y, usar, desde, inicio, dias, ctx);
        if (p == null) {
            elegida = Config.estacional(ALFA_DEFECTO, SHRINK_DEFECTO);
            p = ajustar(elegida, y, usar, desde, inicio, dias, ctx);
        }
        p.calibracion = new CalibracionAyax(elegida.nombre(), elegida.alfa, elegida.croston ? null : elegida.shrink,
                probadas, errorElegida, sesgoElegida, errorDefecto);
        return p;
    }

    /** Suma |error|, real y pronostico de los dias limpios de una ventana de validacion. */
    private static void acumular(double[] acum, double[] y, boolean[] usar, int desde, double[] pred) {
        for (int h = 0; h < pred.length && desde + h < y.length; h++) {
            int i = desde + h;
            if (!usar[i]) continue;
            acum[0] += Math.abs(y[i] - pred[h]);
            acum[1] += y[i];
            acum[2] += pred[h];
        }
    }

    /**
     * {WAPE %, sesgo %}. WAPE = sum|real - pred| / sum(real).
     * Sesgo = (sum(pred) - sum(real)) / sum(real): + sobra, - falta.
     */
    private static double[] metricas(double[] acum) {
        if (acum[1] <= 0) {
            // Sin ventas en la validacion: castiga pronosticar de mas
            return new double[]{100.0 * acum[0], 100.0 * acum[2]};
        }
        return new double[]{100.0 * acum[0] / acum[1], 100.0 * (acum[2] - acum[1]) / acum[1]};
    }

    /** Puntaje de la autocalibracion: equivocarse poco Y no cargarse hacia un lado. */
    private static double puntaje(double[] m) {
        return m[0] + Math.abs(m[1]);
    }

    private Pronostico ajustar(Config cfg, double[] y, boolean[] usar, LocalDate desde, LocalDate inicio, int dias,
                               Map<LocalDate, ContextoDto> ctx) {
        return cfg.croston
                ? croston(y, usar, cfg.alfa, dias)
                : estacional(y, usar, desde, inicio, dias, ctx, cfg.alfa, cfg.shrink);
    }

    // =====================================================================
    // Ayax-Estacional
    // =====================================================================

    private Pronostico estacional(double[] y, boolean[] usarOriginal, LocalDate desde, LocalDate inicio, int dias,
                                  Map<LocalDate, ContextoDto> ctx, double alfa, double shrink) {
        int n = y.length;
        boolean[] usar = usarOriginal.clone();
        double media = promedioMascara(y, usar, 0, n);
        if (media <= 0) {
            // Solo vendio en dias atipicos: mejor usar todo que pronosticar cero
            Arrays.fill(usar, true);
            media = total(y) / n;
        }
        if (media <= 0) return Pronostico.ceros(dias, null);

        ContextoDto[] c = new ContextoDto[n];
        for (int i = 0; i < n; i++) c[i] = contexto(ctx, desde.plusDays(i));

        // 1) Dia de la semana
        double[] sumaDow = new double[8];
        int[] nDow = new int[8];
        for (int i = 0; i < n; i++) {
            if (!usar[i]) continue;
            int d = desde.plusDays(i).getDayOfWeek().getValue();
            sumaDow[d] += y[i];
            nDow[d]++;
        }
        double[] fDow = new double[8];
        for (int d = 1; d <= 7; d++) {
            fDow[d] = (sumaDow[d] + shrink * media) / ((nDow[d] + shrink) * media);
        }

        // Serie relativa sin efecto de dia de semana
        double[] rel = new double[n];
        final double mediaF = media;
        for (int i = 0; i < n; i++) rel[i] = y[i] / (mediaF * fDow[desde.plusDays(i).getDayOfWeek().getValue()]);

        // 2) Quincena y evento: promedio relativo con la marca vs sin la marca
        double fQuin = factorBinario(rel, usar, shrink, i -> Boolean.TRUE.equals(c[i].esQuincena()));
        double fEvento = factorBinario(rel, usar, shrink, i -> c[i].tieneEvento());

        // 3) Temporada contra NORMAL
        Map<Temporada, double[]> acumTemp = new EnumMap<>(Temporada.class);
        for (int i = 0; i < n; i++) {
            if (!usar[i]) continue;
            Temporada t = c[i].temporada() == null ? Temporada.NORMAL : c[i].temporada();
            double[] a = acumTemp.computeIfAbsent(t, k -> new double[2]);
            a[0] += rel[i];
            a[1]++;
        }
        double[] normal = acumTemp.getOrDefault(Temporada.NORMAL, new double[]{0, 0});
        double baseNormal = (normal[0] + shrink) / (normal[1] + shrink);
        Map<Temporada, Double> fTemp = new EnumMap<>(Temporada.class);
        for (Temporada t : Temporada.values()) {
            double[] a = acumTemp.getOrDefault(t, new double[]{0, 0});
            fTemp.put(t, t == Temporada.NORMAL ? 1.0 : acotar(((a[0] + shrink) / (a[1] + shrink)) / baseNormal, 0.5, 2.5));
        }

        // 4) Nivel por suavizamiento exponencial con errores a un paso (los atipicos no cuentan)
        double[] z = new double[n];
        for (int i = 0; i < n; i++) {
            z[i] = y[i] / (media * factores(desde.plusDays(i), c[i], fDow, fQuin, fEvento, fTemp));
        }
        int arranque = 0;
        int limpiosArranque = 0;
        double nivel = 0;
        while (arranque < n && limpiosArranque < 7) {
            if (usar[arranque]) {
                nivel += z[arranque];
                limpiosArranque++;
            }
            arranque++;
        }
        nivel = limpiosArranque == 0 ? 1.0 : nivel / limpiosArranque;

        double sumaErr2 = 0;
        int nErr = 0;
        int inicioErr = Math.max(arranque, n - 56);
        for (int i = arranque; i < n; i++) {
            if (!usar[i]) continue;
            double ajuste = media * nivel * factores(desde.plusDays(i), c[i], fDow, fQuin, fEvento, fTemp);
            if (i >= inicioErr) {
                sumaErr2 += Math.pow(y[i] - ajuste, 2);
                nErr++;
            }
            nivel = alfa * z[i] + (1 - alfa) * nivel;
        }
        double sigma = nErr > 1 ? Math.sqrt(sumaErr2 / (nErr - 1)) : Math.sqrt(media);

        // 5) Tendencia: ultimas 4 semanas vs las 4 anteriores (solo dias limpios), acotada a +-1 % diario
        double tendencia = 1.0;
        if (n >= 56 && contar(usar, n - 28, n) >= 7 && contar(usar, n - 56, n - 28) >= 7) {
            double reciente = promedioMascara(z, usar, n - 28, n);
            double previa = promedioMascara(z, usar, n - 56, n - 28);
            if (previa > 0 && reciente > 0) {
                tendencia = acotar(Math.pow(reciente / previa, 1.0 / 28), 0.99, 1.01);
            }
        }

        // 6) Pronostico
        Pronostico p = new Pronostico(dias);
        for (int h = 0; h < dias; h++) {
            LocalDate f = inicio.plusDays(h);
            double fac = factores(f, contexto(ctx, f), fDow, fQuin, fEvento, fTemp);
            double pred = Math.max(0, media * nivel * Math.pow(tendencia, h + 1) * fac);
            double banda = Z80 * sigma * Math.sqrt(fac) * Math.sqrt(1 + 0.03 * h);
            p.pred[h] = pred;
            p.inf[h] = Math.max(0, pred - banda);
            p.sup[h] = pred + banda;
        }
        return p;
    }

    // =====================================================================
    // Ayax-Intermitente (Croston con correccion SBA)
    // =====================================================================

    /**
     * Croston: cuando hay venta, suaviza el tamano (z) y el intervalo entre ventas (p).
     * Tasa diaria = (1 - alfa/2) * z / p. La correccion (1 - alfa/2) es de Syntetos-Boylan (SBA)
     * y quita el sesgo hacia arriba que tiene el Croston original.
     * Solo usa dias limpios; los dias atipicos no cuentan ni como venta ni como intervalo.
     */
    private Pronostico croston(double[] y, boolean[] usar, double alfa, int dias) {
        int n = y.length;
        double z = -1, p = -1;
        int intervalo = 1;
        double sumaErr2 = 0;
        int nErr = 0;
        int inicioErr = Math.max(0, n - 56);
        int conVenta = 0;

        for (int i = 0; i < n; i++) {
            if (!usar[i]) continue;
            if (z > 0 && i >= inicioErr) {
                double tasa = (1 - alfa / 2) * z / p;
                sumaErr2 += Math.pow(y[i] - tasa, 2);
                nErr++;
            }
            if (y[i] > 0) {
                conVenta++;
                if (z < 0) {           // primera venta: inicializa
                    z = y[i];
                    p = intervalo;
                } else {
                    z = alfa * y[i] + (1 - alfa) * z;
                    p = alfa * intervalo + (1 - alfa) * p;
                }
                intervalo = 1;
            } else {
                intervalo++;
            }
        }
        if (conVenta < 2 || z <= 0 || p <= 0) {
            return null; // no hay con que suavizar: que gane el metodo estacional
        }

        double tasa = (1 - alfa / 2) * z / p;
        double sigma = nErr > 1 ? Math.sqrt(sumaErr2 / (nErr - 1)) : Math.sqrt(tasa);
        Pronostico r = new Pronostico(dias);
        for (int h = 0; h < dias; h++) {
            double banda = Z80 * sigma * Math.sqrt(1 + 0.03 * h);
            r.pred[h] = tasa;
            r.inf[h] = Math.max(0, tasa - banda);
            r.sup[h] = tasa + banda;
        }
        return r;
    }

    private static boolean esIntermitente(double[] y, boolean[] usar) {
        int conVenta = 0;
        for (int i = 0; i < y.length; i++) if (usar[i] && y[i] > 0) conVenta++;
        return conVenta >= MIN_DIAS_CON_VENTA && fraccionCeros(y, usar) >= FRACCION_CEROS_INTERMITENTE;
    }

    private static double fraccionCeros(double[] y, boolean[] usar) {
        int limpios = 0, ceros = 0;
        for (int i = 0; i < y.length; i++) {
            if (!usar[i]) continue;
            limpios++;
            if (y[i] <= 0) ceros++;
        }
        return limpios == 0 ? 0 : (double) ceros / limpios;
    }

    // =====================================================================
    // Dias limpios
    // =====================================================================

    /**
     * true = el dia sirve para aprender. Excluye promociones y rachas de ceros que parecen
     * quiebre de stock. Si quedan menos de MIN_DIAS_LIMPIOS, regresa todo en true.
     */
    static boolean[] diasLimpios(double[] y, boolean[] promo) {
        int n = y.length;
        boolean[] usar = new boolean[n];
        for (int i = 0; i < n; i++) usar[i] = promo == null || i >= promo.length || !promo[i];

        // Venta tipica en dias sin promocion, para decidir si un cero es raro
        double mediaSinPromo = promedioMascara(y, usar, 0, n);
        if (mediaSinPromo >= VENTA_MIN_QUIEBRE) {
            int i = 0;
            while (i < n) {
                if (y[i] > 0) {
                    i++;
                    continue;
                }
                int j = i;
                while (j < n && y[j] <= 0) j++;
                if (j - i >= RACHA_QUIEBRE) {
                    for (int k = i; k < j; k++) usar[k] = false;
                }
                i = j;
            }
        }

        if (contar(usar, 0, n) < Math.min(MIN_DIAS_LIMPIOS, n)) {
            Arrays.fill(usar, true);
        }
        return usar;
    }

    // =====================================================================
    // Utilidades
    // =====================================================================

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

    private static double factorBinario(double[] rel, boolean[] usar, double shrink, Marca marca) {
        double sCon = 0, nCon = 0, sSin = 0, nSin = 0;
        for (int i = 0; i < rel.length; i++) {
            if (!usar[i]) continue;
            if (marca.en(i)) {
                sCon += rel[i];
                nCon++;
            } else {
                sSin += rel[i];
                nSin++;
            }
        }
        double con = (sCon + shrink) / (nCon + shrink);
        double sin = (sSin + shrink) / (nSin + shrink);
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

    private static double promedioMascara(double[] v, boolean[] usar, int desde, int hasta) {
        double s = 0;
        int k = 0;
        for (int i = desde; i < hasta; i++) {
            if (usar[i]) {
                s += v[i];
                k++;
            }
        }
        return k == 0 ? 0 : s / k;
    }

    private static int contar(boolean[] usar, int desde, int hasta) {
        int k = 0;
        for (int i = desde; i < hasta; i++) if (usar[i]) k++;
        return k;
    }

    private static double acotar(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    private static double r2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    /** Una configuracion candidata de la autocalibracion. */
    private record Config(boolean croston, double alfa, double shrink) {
        static Config estacional(double alfa, double shrink) {
            return new Config(false, alfa, shrink);
        }

        static Config croston(double alfa) {
            return new Config(true, alfa, 0);
        }

        String nombre() {
            return croston ? "Ayax-Intermitente" : "Ayax-Estacional";
        }
    }

    /** Pronostico de un producto: arreglos paralelos por dia. */
    private static final class Pronostico {
        final double[] pred;
        final double[] inf;
        final double[] sup;
        CalibracionAyax calibracion;

        Pronostico(int dias) {
            pred = new double[dias];
            inf = new double[dias];
            sup = new double[dias];
        }

        static Pronostico ceros(int dias, CalibracionAyax cal) {
            Pronostico p = new Pronostico(dias);
            p.calibracion = cal;
            return p;
        }
    }
}
