package mx.Tienda_inteligente.pls.promocion;

import mx.Tienda_inteligente.pls.analitica.EstadisticasVentas;
import mx.Tienda_inteligente.pls.analitica.SerieDiaria;
import mx.Tienda_inteligente.pls.common.Dinero;
import mx.Tienda_inteligente.pls.configuracion.Configuracion;
import mx.Tienda_inteligente.pls.contexto.ContextoDto;
import mx.Tienda_inteligente.pls.contexto.ContextoService;
import mx.Tienda_inteligente.pls.contexto.Temporada;
import mx.Tienda_inteligente.pls.inventario.AnalisisInventario;
import mx.Tienda_inteligente.pls.inventario.EstadoProducto;
import mx.Tienda_inteligente.pls.inventario.Semaforo;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Motor de recomendaciones de promociones (algoritmo BASE, sin IA).
 *
 * <p>Para cada producto detecta POR QUE convendria una promocion:
 * <ul>
 *   <li><b>POR_CADUCAR</b>: unidades que no se van a vender antes de su caducidad estimada.</li>
 *   <li><b>SOBRESTOCK</b>: mas inventario del necesario (dinero detenido).</li>
 *   <li><b>BAJA_ROTACION</b>: producto que casi no se mueve.</li>
 *   <li><b>TEMPORADA</b>: se acerca un evento/temporada en la que su categoria vende mas.</li>
 * </ul>
 *
 * <p>Despues prueba descuentos de 5 % en 5 % (hasta el tope de la configuracion y sin romper
 * el margen minimo) y elige el que maximiza la ganancia INCREMENTAL contra no hacer nada:
 * <pre>
 *   unidadesSin = demanda diaria x dias x factor temporada
 *   unidadesCon = unidadesSin x (1 + elasticidad x descuento)      (tope: stock disponible)
 *   ganancia    = unidades vendidas x (precio - costo) - unidades que caducan x costo + capital liberado
 * </pre>
 * La elasticidad se aprende de promociones pasadas de la misma categoria; si no hay, se usa 2.0
 * (10 % de descuento = 20 % mas unidades). Solo se sugiere si la ganancia incremental es positiva.
 */
@Component
public class MotorPromociones {

    static final double ELASTICIDAD_DEFECTO = 2.0;
    /** Costo mensual de mantener inventario (almacen, capital, obsolescencia) como fraccion del costo. */
    static final double COSTO_MANTENER_MENSUAL = 0.03;

    private final AnalisisInventario analisisInventario;
    private final EstadisticasVentas estadisticas;
    private final ContextoService contextoService;
    private final PromocionRepository promocionRepository;

    public MotorPromociones(AnalisisInventario analisisInventario, EstadisticasVentas estadisticas,
                            ContextoService contextoService, PromocionRepository promocionRepository) {
        this.analisisInventario = analisisInventario;
        this.estadisticas = estadisticas;
        this.contextoService = contextoService;
        this.promocionRepository = promocionRepository;
    }

    /** Sugerencia calculada (todavia no se guarda). */
    public record Sugerencia(
            EstadoProducto producto,
            MotivoPromocion motivo,
            double descuento,          // fraccion 0-1
            LocalDate fechaInicio,
            LocalDate fechaFin,
            int ventasEsperadas,
            double ventasSinPromo,
            double gananciaIncremental,
            double perdidaEvitada,
            double elasticidad,
            double factorTemporada,
            String evento,
            String explicacion
    ) {
        public int dias() {
            return (int) ChronoUnit.DAYS.between(fechaInicio, fechaFin) + 1;
        }
    }

    public List<Sugerencia> sugerir(Configuracion conf, int horizonteDias) {
        LocalDate hoy = LocalDate.now();
        double descuentoMax = conf.getDescuentoMaximo().doubleValue() / 100.0;
        double margenMin = conf.getMargenMinimoPromo().doubleValue();

        List<EstadoProducto> estados = analisisInventario.calcular();
        Map<String, Double> elasticidades = elasticidadesPorCategoria(estados);
        Map<LocalDate, ContextoDto> futuro = contextoService.mapa(hoy, hoy.plusDays(horizonteDias));
        Map<String, Map<String, Double>> efectoEventos = efectoEventosPorCategoria(estados, hoy);

        List<Sugerencia> sugerencias = new ArrayList<>();
        for (EstadoProducto e : estados) {
            if (e.stockActual() <= 0 || e.precioVenta().signum() <= 0) continue;
            double precio = e.precioVenta().doubleValue();
            double costo = e.costoUnitario().doubleValue();
            double dTopeMargen = 1 - (costo * (1 + margenMin)) / precio;
            double dMax = Math.min(descuentoMax, dTopeMargen);
            if (dMax < 0.05) continue; // el margen no permite promocion

            double elasticidad = elasticidades.getOrDefault(e.categoria(), ELASTICIDAD_DEFECTO);
            List<Sugerencia> candidatas = new ArrayList<>();

            // 1) Por caducar
            if (e.unidadesEnRiesgo() != null && e.unidadesEnRiesgo() > 0 && e.diasParaCaducar() != null
                    && e.diasParaCaducar() >= 0 && e.diasParaCaducar() <= 7) {
                int dias = Math.max(1, e.diasParaCaducar());
                optimizar(e, MotivoPromocion.POR_CADUCAR, hoy, hoy.plusDays(dias - 1L), 1.0, null,
                        elasticidad, dMax, true).ifPresent(candidatas::add);
            }
            // 2) Sobrestock
            if (e.semaforo() == Semaforo.SOBRESTOCK && e.demandaDiaria() > 0) {
                optimizar(e, MotivoPromocion.SOBRESTOCK, hoy.plusDays(1), hoy.plusDays(7), 1.0, null,
                        elasticidad, dMax, false).ifPresent(candidatas::add);
            }
            // 3) Baja rotacion: menos de media rotacion al mes
            double rotacionMensual = e.stockActual() > 0 ? e.demandaDiaria() * 30 / e.stockActual() : 0;
            if (e.semaforo() != Semaforo.SOBRESTOCK && e.demandaDiaria() > 0 && rotacionMensual < 0.5) {
                optimizar(e, MotivoPromocion.BAJA_ROTACION, hoy.plusDays(1), hoy.plusDays(7), 1.0, null,
                        elasticidad, dMax, false).ifPresent(candidatas::add);
            }
            // 4) Temporada / evento proximo en el que la categoria vende mas
            ventanaTemporada(futuro, hoy, efectoEventos.getOrDefault(e.categoria(), Map.of())).ifPresent(v -> {
                boolean stockSano = e.diasCobertura() != null && e.diasCobertura() > v.dias + e.leadTimeDias();
                if (stockSano && e.demandaDiaria() > 0) {
                    optimizar(e, MotivoPromocion.TEMPORADA, v.inicio, v.fin, v.factor, v.nombre,
                            elasticidad, Math.min(dMax, 0.20), false).ifPresent(candidatas::add);
                }
            });

            candidatas.stream().max(Comparator.comparingDouble(Sugerencia::gananciaIncremental))
                    .ifPresent(sugerencias::add);
        }
        sugerencias.sort(Comparator.comparingDouble(Sugerencia::gananciaIncremental).reversed());
        return sugerencias;
    }

    /** Prueba descuentos y regresa el de mayor ganancia incremental (si es positiva). */
    Optional<Sugerencia> optimizar(EstadoProducto e, MotivoPromocion motivo, LocalDate inicio, LocalDate fin,
                                   double factorTemporada, String evento, double elasticidad, double dMax,
                                   boolean caduca) {
        Sugerencia mejor = null;
        List<Double> pruebas = new ArrayList<>();
        for (double d = 0.05; d < dMax - 1e-9; d += 0.05) pruebas.add(Math.round(d * 100) / 100.0);
        pruebas.add(Math.floor(dMax * 100) / 100.0);
        for (double d : pruebas) {
            Sugerencia s = evaluar(e, motivo, inicio, fin, factorTemporada, evento, elasticidad, d, caduca);
            if (s.gananciaIncremental() > 0 && (mejor == null || s.gananciaIncremental() > mejor.gananciaIncremental())) {
                mejor = s;
            }
        }
        return Optional.ofNullable(mejor);
    }

    /** Calcula ventas y ganancia esperadas para un descuento dado (lo reutiliza la IA para validar). */
    public Sugerencia evaluar(EstadoProducto e, MotivoPromocion motivo, LocalDate inicio, LocalDate fin,
                              double factorTemporada, String evento, double elasticidad, double d, boolean caduca) {
        int dias = (int) ChronoUnit.DAYS.between(inicio, fin) + 1;
        double precio = e.precioVenta().doubleValue();
        double costo = e.costoUnitario().doubleValue();
        int stock = e.stockActual();

        double uSin = e.demandaDiaria() * dias * factorTemporada;
        double uCon = uSin * (1 + elasticidad * d);
        double vendSin = Math.min(stock, uSin);
        double vendCon = Math.min(stock, uCon);

        double gananciaSin = vendSin * (precio - costo);
        double gananciaCon = vendCon * (precio * (1 - d) - costo);
        double perdidaEvitada = 0;
        if (caduca) {
            double caducanSin = Math.max(0, stock - uSin);
            double caducanCon = Math.max(0, stock - uCon);
            gananciaSin -= caducanSin * costo;
            gananciaCon -= caducanCon * costo;
            perdidaEvitada = (caducanSin - caducanCon) * costo;
        } else if (motivo == MotivoPromocion.SOBRESTOCK || motivo == MotivoPromocion.BAJA_ROTACION) {
            // Cada unidad extra vendida hoy deja de costar "mantenerla" los meses que tardaria en venderse
            double mesesCobertura = e.diasCobertura() == null ? 6 : Math.min(12, e.diasCobertura() / 30.0);
            perdidaEvitada = (vendCon - vendSin) * costo * COSTO_MANTENER_MENSUAL * mesesCobertura;
            gananciaCon += perdidaEvitada;
        }
        double incremental = gananciaCon - gananciaSin;
        String explicacion = explicar(e, motivo, d, dias, uSin, vendCon, incremental, perdidaEvitada, evento, factorTemporada, caduca);
        return new Sugerencia(e, motivo, d, inicio, fin, (int) Math.round(vendCon), Dinero.r2(uSin),
                Dinero.r2(incremental), Dinero.r2(perdidaEvitada), elasticidad, factorTemporada, evento, explicacion);
    }

    private String explicar(EstadoProducto e, MotivoPromocion motivo, double d, int dias, double uSin, double vendCon,
                            double incremental, double perdidaEvitada, String evento, double factor, boolean caduca) {
        int pct = (int) Math.round(d * 100);
        String fin = String.format(" Con %d%% de descuento se esperan vender ~%d y ganar $%.2f mas que sin promocion.",
                pct, Math.round(vendCon), incremental);
        return switch (motivo) {
            case POR_CADUCAR -> String.format("%d unidades caducan en %d dias; sin promo se venderian ~%d y se perderian $%.2f.",
                    e.unidadesEnRiesgo(), Math.max(0, e.diasParaCaducar()), Math.round(uSin),
                    Math.max(0, e.stockActual() - uSin) * e.costoUnitario().doubleValue()) + fin;
            case SOBRESTOCK -> String.format("Sobrestock: %d unidades cubren %s dias de venta ($%s detenidos).",
                    e.stockActual(), e.diasCobertura() == null ? "muchos" : String.valueOf(Math.round(e.diasCobertura())),
                    e.valorInventario()) + fin;
            case BAJA_ROTACION -> String.format("Baja rotacion: se venden ~%.1f unidades al dia con %d en existencia.",
                    e.demandaDiaria(), e.stockActual()) + fin;
            case TEMPORADA -> String.format("%s: en fechas similares la categoria %s vende %.0f%% mas.",
                    evento, e.categoria(), (factor - 1) * 100) + fin;
        };
    }

    // ------------------------------------------------------------------ elasticidad

    /**
     * Elasticidad observada en promociones pasadas: (incremento % de unidades) / (descuento %).
     * Se promedia por categoria y se acota a [0.5, 6].
     */
    Map<String, Double> elasticidadesPorCategoria(List<EstadoProducto> estados) {
        Map<Long, EstadoProducto> porId = new HashMap<>();
        estados.forEach(e -> porId.put(e.productoId(), e));
        Map<String, double[]> acumulado = new HashMap<>();
        LocalDate hoy = LocalDate.now();
        List<Promocion> pasadas = new ArrayList<>(promocionRepository.findByEstado(EstadoPromocion.FINALIZADA));
        pasadas.addAll(promocionRepository.findByEstado(EstadoPromocion.ACTIVA));
        for (Promocion p : pasadas) {
            EstadoProducto e = porId.get(p.getProductoId());
            if (e == null || p.getFechaInicio().isAfter(hoy.minusDays(1))) continue;
            LocalDate fin = p.getFechaFin().isAfter(hoy) ? hoy : p.getFechaFin();
            SerieDiaria s = estadisticas.series(p.getFechaInicio().minusDays(28), fin, List.of(p.getProductoId()))
                    .get(p.getProductoId());
            if (s == null) continue;
            int diasPromo = (int) ChronoUnit.DAYS.between(p.getFechaInicio(), fin) + 1;
            double antes = 0, durante = 0;
            for (int i = 0; i < 28; i++) antes += s.valores()[i];
            for (int i = 28; i < s.dias(); i++) durante += s.valores()[i];
            antes /= 28;
            durante /= Math.max(1, diasPromo);
            double d = CalculadoraPromocion.porcentajeDescuento(p.getTipo(), p.getValor(), e.precioVenta()).doubleValue() / 100;
            if (antes <= 0 || d <= 0) continue;
            double el = (durante / antes - 1) / d;
            double[] a = acumulado.computeIfAbsent(e.categoria(), k -> new double[2]);
            a[0] += Math.max(0.5, Math.min(6, el));
            a[1]++;
        }
        Map<String, Double> r = new HashMap<>();
        acumulado.forEach((cat, a) -> r.put(cat, Dinero.r2(a[0] / a[1])));
        return r;
    }

    // ------------------------------------------------------------------ temporada

    /**
     * Por categoria: cuanto mas vende en cada temporada (clave = nombre de temporada) y en dias
     * con evento (clave "EVENTO"), contra dias normales. Usa el ultimo anio de historial.
     */
    Map<String, Map<String, Double>> efectoEventosPorCategoria(List<EstadoProducto> estados, LocalDate hoy) {
        LocalDate desde = hoy.minusDays(365);
        LocalDate hasta = hoy.minusDays(1);
        Map<Long, SerieDiaria> series = estadisticas.series(desde, hasta, null);
        Map<LocalDate, ContextoDto> ctx = contextoService.mapa(desde, hasta);

        Map<String, double[]> porCategoriaDia = new HashMap<>();
        int n = (int) ChronoUnit.DAYS.between(desde, hasta) + 1;
        for (EstadoProducto e : estados) {
            SerieDiaria s = series.get(e.productoId());
            if (s == null) continue;
            double[] acc = porCategoriaDia.computeIfAbsent(e.categoria(), k -> new double[n]);
            for (int i = 0; i < n; i++) acc[i] += s.valores()[i];
        }

        Map<String, Map<String, Double>> r = new HashMap<>();
        porCategoriaDia.forEach((cat, v) -> {
            Map<String, double[]> grupos = new HashMap<>();
            for (int i = 0; i < n; i++) {
                ContextoDto c = ctx.get(desde.plusDays(i));
                if (c == null) continue;
                String clave = c.tieneEvento() ? "EVENTO"
                        : (c.temporada() == null ? Temporada.NORMAL.name() : c.temporada().name());
                double[] g = grupos.computeIfAbsent(clave, k -> new double[2]);
                g[0] += v[i];
                g[1]++;
            }
            double[] normal = grupos.get(Temporada.NORMAL.name());
            if (normal == null || normal[1] < 14 || normal[0] == 0) return;
            double baseNormal = normal[0] / normal[1];
            Map<String, Double> efectos = new HashMap<>();
            grupos.forEach((clave, g) -> {
                if (!clave.equals(Temporada.NORMAL.name()) && g[1] >= 3) {
                    efectos.put(clave, (g[0] / g[1]) / baseNormal);
                }
            });
            r.put(cat, efectos);
        });
        return r;
    }

    private record Ventana(LocalDate inicio, LocalDate fin, int dias, double factor, String nombre) {
    }

    /** Primer evento o temporada del horizonte donde la categoria vende al menos 15 % mas. */
    private Optional<Ventana> ventanaTemporada(Map<LocalDate, ContextoDto> futuro, LocalDate hoy,
                                               Map<String, Double> efectos) {
        if (efectos.isEmpty()) return Optional.empty();
        List<LocalDate> fechas = futuro.keySet().stream().sorted().toList();
        for (int i = 0; i < fechas.size(); i++) {
            LocalDate f = fechas.get(i);
            if (!f.isAfter(hoy)) continue;
            ContextoDto c = futuro.get(f);
            String clave = c.tieneEvento() ? "EVENTO"
                    : (c.temporada() == null || c.temporada() == Temporada.NORMAL ? null : c.temporada().name());
            if (clave == null) continue;
            Double factor = efectos.get(clave);
            if (factor == null || factor < 1.15) continue;
            // la ventana dura mientras siga el mismo evento/temporada (max 7 dias)
            LocalDate fin = f;
            String nombre = c.tieneEvento() ? c.nombreEvento() : "Temporada " + c.temporada().name().toLowerCase().replace('_', ' ');
            for (int j = i + 1; j < fechas.size() && ChronoUnit.DAYS.between(f, fechas.get(j)) < 7; j++) {
                ContextoDto cj = futuro.get(fechas.get(j));
                boolean mismo = "EVENTO".equals(clave)
                        ? (cj.tieneEvento() && java.util.Objects.equals(cj.nombreEvento(), c.nombreEvento()))
                        : cj.temporada() == c.temporada();
                if (!mismo) break;
                fin = fechas.get(j);
            }
            return Optional.of(new Ventana(f, fin, (int) ChronoUnit.DAYS.between(f, fin) + 1,
                    Math.min(factor, 3.0), nombre));
        }
        return Optional.empty();
    }

    static BigDecimal porcentaje(double fraccion) {
        return BigDecimal.valueOf(Math.round(fraccion * 100));
    }
}
