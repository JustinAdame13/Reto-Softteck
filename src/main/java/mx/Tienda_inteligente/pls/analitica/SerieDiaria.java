package mx.Tienda_inteligente.pls.analitica;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Unidades vendidas por dia de un producto, con ceros en los dias sin venta.
 * valores[0] corresponde a 'desde'.
 */
public record SerieDiaria(Long productoId, LocalDate desde, double[] valores) {

    public int dias() {
        return valores.length;
    }

    public LocalDate fecha(int i) {
        return desde.plusDays(i);
    }

    public double valor(LocalDate fecha) {
        int i = (int) ChronoUnit.DAYS.between(desde, fecha);
        return (i < 0 || i >= valores.length) ? 0 : valores[i];
    }

    public double total() {
        double s = 0;
        for (double v : valores) s += v;
        return s;
    }

    public double promedio() {
        return valores.length == 0 ? 0 : total() / valores.length;
    }

    /** Promedio de los ultimos n dias. */
    public double promedioUltimos(int n) {
        int k = Math.min(n, valores.length);
        if (k == 0) return 0;
        double s = 0;
        for (int i = valores.length - k; i < valores.length; i++) s += valores[i];
        return s / k;
    }

    public double desviacionUltimos(int n) {
        int k = Math.min(n, valores.length);
        if (k < 2) return 0;
        double media = promedioUltimos(n);
        double s = 0;
        for (int i = valores.length - k; i < valores.length; i++) s += Math.pow(valores[i] - media, 2);
        return Math.sqrt(s / (k - 1));
    }

    /** Dias desde la ultima venta (dias() si nunca se vendio en la ventana). */
    public int diasSinVenta() {
        for (int i = valores.length - 1; i >= 0; i--) {
            if (valores[i] > 0) return valores.length - 1 - i;
        }
        return valores.length;
    }
}
