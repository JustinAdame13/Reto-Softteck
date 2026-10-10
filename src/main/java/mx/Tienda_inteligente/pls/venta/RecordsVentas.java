package mx.Tienda_inteligente.pls.venta;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Records calculados al vuelo sobre la tabla ventas (no se guardan). */
public record RecordsVentas(
        LocalDate desde,
        LocalDate hasta,
        Totales totales,
        Dia mejorDia,
        Hora mejorHora,
        DiaSemana mejorDiaSemana,
        ProductoTop productoEstrella,
        ProductoTop productoMasRentable,
        EmpleadoTop mejorEmpleado,
        TicketTop ticketMasAlto
) {
    public record Totales(BigDecimal ingresos, BigDecimal margen, long unidades, long tickets, BigDecimal ticketPromedio) {}

    public record Dia(LocalDate fecha, BigDecimal ingresos, long tickets) {}

    public record Hora(int hora, BigDecimal ingresos, long tickets, double ticketsPromedioPorDia) {}

    public record DiaSemana(int diaSemana, String nombre, BigDecimal ingresos, long tickets) {}

    public record ProductoTop(Long productoId, String sku, String nombre, long unidades, BigDecimal ingresos, BigDecimal margen) {}

    public record EmpleadoTop(Long empleadoId, String nombre, BigDecimal ingresos, long tickets) {}

    public record TicketTop(String ticket, LocalDate fecha, BigDecimal total, long articulos) {}
}
