package mx.Tienda_inteligente.pls.contexto;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Month;
import java.time.MonthDay;
import java.time.temporal.TemporalAdjusters;

/**
 * Calcula el contexto "calculable" de cualquier dia: quincenas, festivos oficiales (LFT art. 74),
 * eventos comerciales y temporadas. Lo usa /contexto/generar y tambien el motor de prediccion
 * para fechas que todavia no existen en la tabla.
 */
public final class CalendarioMexico {

    private CalendarioMexico() {
    }

    public static ContextoDto calcular(LocalDate f) {
        String festivo = festivoOficial(f);
        String evento = festivo != null ? festivo : eventoComercial(f);
        return new ContextoDto(f, festivo != null, evento, esQuincena(f), temporada(f), null, null);
    }

    /** Dias de pago: 15 y ultimo dia del mes (mas el dia siguiente, cuando se gasta el pago). */
    public static boolean esQuincena(LocalDate f) {
        int dia = f.getDayOfMonth();
        int ultimo = f.lengthOfMonth();
        return dia == 15 || dia == 16 || dia == ultimo || dia == 1;
    }

    public static Temporada temporada(LocalDate f) {
        MonthDay md = MonthDay.from(f);
        if (!md.isBefore(MonthDay.of(12, 1)) || !md.isAfter(MonthDay.of(1, 6))) {
            return Temporada.NAVIDAD;          // 1 dic - 6 ene (Reyes)
        }
        if (!md.isBefore(MonthDay.of(7, 10)) && md.isBefore(MonthDay.of(8, 10))) {
            return Temporada.VERANO;           // vacaciones escolares SEP
        }
        if (!md.isBefore(MonthDay.of(8, 10)) && !md.isAfter(MonthDay.of(9, 5))) {
            return Temporada.REGRESO_CLASES;
        }
        return Temporada.NORMAL;
    }

    /** Descanso obligatorio segun la Ley Federal del Trabajo. */
    public static String festivoOficial(LocalDate f) {
        int anio = f.getYear();
        if (f.equals(LocalDate.of(anio, 1, 1))) return "Anio Nuevo";
        if (f.equals(nLunes(anio, Month.FEBRUARY, 1))) return "Dia de la Constitucion";
        if (f.equals(nLunes(anio, Month.MARCH, 3))) return "Natalicio de Benito Juarez";
        if (f.equals(LocalDate.of(anio, 5, 1))) return "Dia del Trabajo";
        if (f.equals(LocalDate.of(anio, 9, 16))) return "Dia de la Independencia";
        if (f.equals(LocalDate.of(anio, 10, 1)) && (anio - 2024) % 6 == 0) return "Transmision del Poder Ejecutivo";
        if (f.equals(nLunes(anio, Month.NOVEMBER, 3))) return "Dia de la Revolucion";
        if (f.equals(LocalDate.of(anio, 12, 25))) return "Navidad";
        return null;
    }

    public static String eventoComercial(LocalDate f) {
        int anio = f.getYear();
        if (f.equals(LocalDate.of(anio, 1, 6))) return "Dia de Reyes";
        if (f.equals(LocalDate.of(anio, 2, 14))) return "San Valentin";
        if (f.equals(LocalDate.of(anio, 4, 30))) return "Dia del Nino";
        if (f.equals(LocalDate.of(anio, 5, 10))) return "Dia de las Madres";
        if (f.equals(LocalDate.of(anio, Month.JUNE, 1).with(TemporalAdjusters.dayOfWeekInMonth(3, DayOfWeek.SUNDAY))))
            return "Dia del Padre";
        if (f.equals(LocalDate.of(anio, 9, 15))) return "Noche Mexicana";
        if (f.equals(LocalDate.of(anio, 10, 31))) return "Halloween";
        if (f.equals(LocalDate.of(anio, 11, 1)) || f.equals(LocalDate.of(anio, 11, 2))) return "Dia de Muertos";
        LocalDate revolucion = nLunes(anio, Month.NOVEMBER, 3);
        if (!f.isBefore(revolucion.minusDays(3)) && f.isBefore(revolucion)) return "El Buen Fin";
        if (f.equals(LocalDate.of(anio, 12, 12))) return "Dia de la Virgen de Guadalupe";
        if (f.equals(LocalDate.of(anio, 12, 24))) return "Nochebuena";
        if (f.equals(LocalDate.of(anio, 12, 31))) return "Fin de Anio";
        return null;
    }

    private static LocalDate nLunes(int anio, Month mes, int n) {
        return LocalDate.of(anio, mes, 1).with(TemporalAdjusters.dayOfWeekInMonth(n, DayOfWeek.MONDAY));
    }
}
