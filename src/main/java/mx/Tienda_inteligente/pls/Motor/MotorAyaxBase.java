package mx.Tienda_inteligente.pls.Motor;

import mx.Tienda_inteligente.pls.Interfaces.MotorPrediccion;
import mx.Tienda_inteligente.pls.RecordsDeDatos.*;
import mx.Tienda_inteligente.pls.Enums.*;
import mx.Tienda_inteligente.pls.prediccion.EstimadorIndices;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class MotorAyaxBase implements MotorPrediccion {

    private final EstimadorIndices estimador = new EstimadorIndices();
    /**
     * Núcleo del motor: recibe datos, devuelve predicciones.
     * Es una función pura: sin side effects, sin BD, sin Spring.
     */
    public List<ResultadoPrediccion> predecir(SolicitudPrediccion solicitud) {
        List<ResultadoPrediccion> resultados = new ArrayList<>();

        LocalDate fechaBase = solicitud.fechaBase();

        for (Long productoId : solicitud.productoIds()) {
            List<VentaDia> historial = solicitud.historialVentas().get(productoId);
            ProductoInfo producto = solicitud.productosInfo().get(productoId);

            // Validación: si no hay datos, saltar
            if (historial == null || historial.isEmpty() || producto == null) {
                continue;
            }

            // Validación: si hay < 28 días, no predecir (muy poco historial)
            if (historial.size() < 28) {
                continue;
            }

            // Índices de ESTE producto, calculados de SU historial.
            // Va aquí y no dentro del for para calcularlo una sola vez
            // por producto en lugar de una vez por día.
            IndicesContexto indices = estimador.estimar(
                    historial, solicitud.contextoPorFecha());


            // Predecir N días hacia adelante
            for (int dia = 1; dia <= solicitud.diasAdelante(); dia++) {
                LocalDate fechaObjetivo = fechaBase.plusDays(dia);

                // ========== PASO 1: Media móvil ponderada ==========
                BigDecimal mediaMovil = calcularMediaMovilPonderada(
                        historial, fechaObjetivo);

                // ========== PASO 2: Factor de tendencia ==========
                double factorTendencia = calcularFactorTendencia(historial);

                // ========== PASO 3: Factor de contexto ==========
                // ========== PASO 3: Factor de contexto ==========
                double factorContexto = calcularFactorContexto(
                        fechaObjetivo,
                        solicitud.contextoPorFecha(),
                        indices);

                // ========== PASO 4: Aplicar ajustes ==========
                BigDecimal prediccion = mediaMovil
                        .multiply(BigDecimal.valueOf(factorTendencia))
                        .multiply(BigDecimal.valueOf(factorContexto))
                        .setScale(2, RoundingMode.HALF_UP);

                // No puede ser negativo
                prediccion = prediccion.max(BigDecimal.ZERO);

                // ========== PASO 5: Intervalo de confianza (90%) ==========
                BigDecimal desviacion = calcularDesviacionEstandar(historial);
                BigDecimal inferior = prediccion.subtract(
                        desviacion.multiply(BigDecimal.valueOf(1.645)));
                BigDecimal superior = prediccion.add(
                        desviacion.multiply(BigDecimal.valueOf(1.645)));

                inferior = inferior.max(BigDecimal.ZERO);

                // ========== PASO 6: Stock recomendado ==========
                int stockRec = calcularStockRecomendado(
                        superior,
                        producto);
                int cantAPedir = Math.max(0, stockRec - producto.stockActual());

                // ========== PASO 7: Armar resultado ==========
                DesgloseFactores desglose = new DesgloseFactores(
                        mediaMovil,
                        factorTendencia,
                        factorContexto,
                        1.0  // Sin IA, el ajuste es 1.0
                );

                ResultadoPrediccion resultado = new ResultadoPrediccion(
                        productoId,
                        fechaObjetivo,
                        prediccion,
                        inferior,
                        superior,
                        stockRec,
                        cantAPedir,
                        Enums.ModeloPrediccion.BASE,
                        LocalDateTime.now(),
                        desglose,
                        null  // Sin IA, sin explicación
                );

                resultados.add(resultado);
            }
        }

        return resultados;
    }

    // ============================================================
    // ALGORITMO 1: Media móvil ponderada con estacionalidad semanal
    // ============================================================
    /**
     * Busca el mismo día de la semana en las últimas 8 semanas.
     * Los días más recientes pesan más.
     * Pesos: [0.25, 0.20, 0.15, 0.12, 0.10, 0.08, 0.06, 0.04]
     */
    private BigDecimal calcularMediaMovilPonderada(
            List<VentaDia> historial,
            LocalDate fechaObjetivo) {

        DayOfWeek diaObjetivo = fechaObjetivo.getDayOfWeek();

        // Filtrar: mismo día de semana, ordenar descendente (recientes primero)
        List<VentaDia> mismosDias = historial.stream()
                .filter(v -> v.fecha().getDayOfWeek() == diaObjetivo)
                .sorted(Comparator.comparing(VentaDia::fecha).reversed())
                .limit(8)
                .collect(Collectors.toList());

        // Fallback: si no hay ese día de la semana, usar promedio general
        if (mismosDias.isEmpty()) {
            return BigDecimal.valueOf(
                    historial.stream()
                            .filter(v -> !v.tuvoPromocion() && !v.huboQuiebreStock())
                            .mapToInt(VentaDia::unidades)
                            .average()
                            .orElse(0)
            );
        }

        // Aplicar pesos (el más reciente es el primero)
        double[] pesos = {0.25, 0.20, 0.15, 0.12, 0.10, 0.08, 0.06, 0.04};
        double sumaProducto = 0;
        double sumaPesos = 0;

        for (int i = 0; i < mismosDias.size() && i < pesos.length; i++) {
            sumaProducto += mismosDias.get(i).unidades() * pesos[i];
            sumaPesos += pesos[i];
        }

        double resultado = sumaPesos > 0 ? sumaProducto / sumaPesos : 0;
        return BigDecimal.valueOf(resultado).setScale(2, RoundingMode.HALF_UP);
    }

    // ============================================================
    // ALGORITMO 2: Factor de tendencia
    // ============================================================
    /**
     * Compara promedio últimos 14 días vs 14 días anteriores.
     * Si sube, factor > 1.0. Si baja, factor < 1.0.
     * Acotado entre 0.7 y 1.5 para evitar valores absurdos.
     */
    private double calcularFactorTendencia(List<VentaDia> historial) {
        if (historial.size() < 28) {
            return 1.0;
        }

        // Filtrar: excluir días con promoción o quiebre (sesgan la tendencia)
        List<VentaDia> diasNormales = historial.stream()
                .filter(v -> !v.tuvoPromocion() && !v.huboQuiebreStock())
                .collect(Collectors.toList());

        if (diasNormales.size() < 28) {
            return 1.0;
        }

        // Últimos 14 días normales
        double promedioReciente = diasNormales.stream()
                .skip(Math.max(0, diasNormales.size() - 14))
                .mapToInt(VentaDia::unidades)
                .average()
                .orElse(0);

        // 14 días anteriores normales
        double promedioAnterior = diasNormales.stream()
                .skip(Math.max(0, diasNormales.size() - 28))
                .limit(14)
                .mapToInt(VentaDia::unidades)
                .average()
                .orElse(0);

        if (promedioAnterior == 0) {
            return 1.0;
        }

        double factor = promedioReciente / promedioAnterior;
        return Math.max(0.7, Math.min(1.5, factor));
    }

    // ============================================================
    // ALGORITMO 3: Factor de contexto
    // ============================================================
    /**
     * Ajusta por festivo, quincena y temporada.
     * Se basa en índices estimados del historial, no valores hardcodeados.
     */
    private double calcularFactorContexto(
            LocalDate fecha,
            Map<LocalDate, ContextoDia> contexto,
            IndicesContexto indices) {

        ContextoDia ctx = contexto.get(fecha);
        if (ctx == null) {
            return 1.0;
        }

        double factor = 1.0;

        if (ctx.esFestivo()) {
            factor *= indices.indiceFestivo();
        }

        if (ctx.esQuincena()) {
            factor *= indices.indiceQuincena();
        }

        factor *= indices.indiceDe(ctx.temporada());

        return Math.min(3.0, factor);
    }

    // ============================================================
    // ALGORITMO 4: Desviación estándar
    // ============================================================
    /**
     * Mide la volatilidad del historial.
     * Se usa para calcular el intervalo de confianza.
     */
    private BigDecimal calcularDesviacionEstandar(List<VentaDia> historial) {
        // Filtrar: solo días normales (sin promo ni quiebre)
        List<VentaDia> diasNormales = historial.stream()
                .filter(v -> !v.tuvoPromocion() && !v.huboQuiebreStock())
                .collect(Collectors.toList());

        if (diasNormales.size() < 2) {
            // Muy poco historial: asumir 30% de volatilidad
            double media = diasNormales.stream()
                    .mapToInt(VentaDia::unidades)
                    .average()
                    .orElse(0);
            return BigDecimal.valueOf(media * 0.30);
        }

        // Calcular media
        double media = diasNormales.stream()
                .mapToInt(VentaDia::unidades)
                .average()
                .orElse(0);

        // Calcular varianza
        double sumaCuadrados = diasNormales.stream()
                .mapToDouble(v -> Math.pow(v.unidades() - media, 2))
                .sum();

        double varianza = sumaCuadrados / (diasNormales.size() - 1);
        double desviacion = Math.sqrt(varianza);

        return BigDecimal.valueOf(desviacion).setScale(2, RoundingMode.HALF_UP);
    }

    // ============================================================
    // ALGORITMO 5: Stock recomendado
    // ============================================================
    /**
     * Fórmula: punto de reorden + stock de seguridad
     *
     * punto_reorden = demanda_superior × leadTime
     * stock_seguridad = √(leadTime) × demanda_diaria × 0.3
     */
    private int calcularStockRecomendado(
            BigDecimal demandaSuperior,
            ProductoInfo producto) {

        double demandaDiaria = demandaSuperior.doubleValue();
        int leadTime = producto.leadTimeDias();

        // Punto de reorden básico
        double puntoReorden = demandaDiaria * leadTime;

        // Stock de seguridad (amortiguador para incertidumbre)
        double safetyStock = Math.sqrt(leadTime) * demandaDiaria * 0.3;

        double stockRecomendado = puntoReorden + safetyStock;

        return (int) Math.ceil(stockRecomendado);
    }
}


