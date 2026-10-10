package mx.Tienda_inteligente.pls.venta;

import mx.Tienda_inteligente.pls.common.ConflictoException;
import mx.Tienda_inteligente.pls.common.Fechas;
import mx.Tienda_inteligente.pls.common.Pagina;
import mx.Tienda_inteligente.pls.common.RecursoNoEncontradoException;
import mx.Tienda_inteligente.pls.common.ReglaNegocioException;
import mx.Tienda_inteligente.pls.empleado.EmpleadoRepository;
import mx.Tienda_inteligente.pls.producto.Producto;
import mx.Tienda_inteligente.pls.producto.ProductoRepository;
import mx.Tienda_inteligente.pls.promocion.CalculadoraPromocion;
import mx.Tienda_inteligente.pls.promocion.Promocion;
import mx.Tienda_inteligente.pls.promocion.PromocionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class VentaService {

    private static final DateTimeFormatter FOLIO = DateTimeFormatter.ofPattern("yyMMddHHmmss");

    private final VentaRepository ventaRepository;
    private final VentaConsultas consultas;
    private final ProductoRepository productoRepository;
    private final PromocionRepository promocionRepository;
    private final EmpleadoRepository empleadoRepository;

    public VentaService(VentaRepository ventaRepository, VentaConsultas consultas,
                        ProductoRepository productoRepository, PromocionRepository promocionRepository,
                        EmpleadoRepository empleadoRepository) {
        this.ventaRepository = ventaRepository;
        this.consultas = consultas;
        this.productoRepository = productoRepository;
        this.promocionRepository = promocionRepository;
        this.empleadoRepository = empleadoRepository;
    }

    /**
     * Registra un ticket completo como en una caja real, todo en una transaccion:
     * 1) toma el precio del producto si no viene, 2) aplica la promocion ACTIVA vigente,
     * 3) descuenta stock (422 si no alcanza), 4) inserta una fila por linea.
     */
    @Transactional
    public TicketResponse registrar(TicketRequest r) {
        String ticket = (r.ticket() == null || r.ticket().isBlank())
                ? "T-" + LocalDateTime.now().format(FOLIO) + ThreadLocalRandom.current().nextInt(100, 1000)
                : r.ticket().trim();
        if (ventaRepository.existsByTicket(ticket)) {
            throw new ConflictoException("El ticket " + ticket + " ya fue registrado");
        }
        LocalDateTime fecha = r.fechaHora() != null ? r.fechaHora() : LocalDateTime.now();
        if (r.empleadoId() != null && !empleadoRepository.existsById(r.empleadoId())) {
            throw RecursoNoEncontradoException.de("Empleado", r.empleadoId());
        }

        Set<Long> ids = r.lineas().stream().map(LineaTicketRequest::productoId).collect(Collectors.toSet());
        Map<Long, Producto> productos = productoRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Producto::getId, Function.identity()));
        for (Long id : ids) {
            Producto p = productos.get(id);
            if (p == null) throw RecursoNoEncontradoException.de("Producto", id);
            if (!p.isActivo()) throw new ReglaNegocioException("El producto " + p.getSku() + " esta inactivo");
        }

        // Validar stock sumando todas las lineas del mismo producto
        Map<Long, Integer> pedido = new LinkedHashMap<>();
        r.lineas().forEach(l -> pedido.merge(l.productoId(), l.cantidad(), Integer::sum));
        Map<String, String> faltantes = new LinkedHashMap<>();
        pedido.forEach((id, cant) -> {
            Producto p = productos.get(id);
            if (p.getStockActual() < cant) {
                faltantes.put("producto " + p.getSku(), "Disponible: " + p.getStockActual() + ", solicitado: " + cant);
            }
        });
        if (!faltantes.isEmpty()) {
            throw new ReglaNegocioException("No hay stock suficiente", faltantes);
        }

        Map<Long, Promocion> promos = new LinkedHashMap<>();
        promocionRepository.activasEn(ids, fecha.toLocalDate()).forEach(pr -> promos.putIfAbsent(pr.getProductoId(), pr));

        List<VentaResponse> lineas = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal descuentoTotal = BigDecimal.ZERO;
        for (LineaTicketRequest l : r.lineas()) {
            Producto p = productos.get(l.productoId());
            BigDecimal precio = l.precioUnitario() != null ? l.precioUnitario() : p.getPrecioVenta();
            Promocion promo = promos.get(p.getId());
            BigDecimal descuento = promo == null ? BigDecimal.ZERO
                    : CalculadoraPromocion.descuentoLinea(promo.getTipo(), promo.getValor(), precio, l.cantidad());

            Venta v = new Venta();
            v.setTicket(ticket);
            v.setFechaHora(fecha);
            v.setProductoId(p.getId());
            v.setCantidad(l.cantidad());
            v.setPrecioUnitario(precio);
            v.setDescuentoAplicado(descuento);
            v.setPromocionId(promo == null ? null : promo.getId());
            v.setEmpleadoId(r.empleadoId());
            v = ventaRepository.save(v);

            BigDecimal bruto = precio.multiply(BigDecimal.valueOf(l.cantidad()));
            subtotal = subtotal.add(bruto);
            descuentoTotal = descuentoTotal.add(descuento);
            lineas.add(new VentaResponse(v.getId(), ticket, fecha, p.getId(), p.getSku(), p.getNombre(),
                    l.cantidad(), precio, descuento, bruto.subtract(descuento), v.getPromocionId(), r.empleadoId()));
        }

        // El producto es una entidad administrada: JPA hace el UPDATE al terminar la transaccion
        pedido.forEach((id, cant) -> {
            Producto p = productos.get(id);
            p.setStockActual(p.getStockActual() - cant);
        });

        return new TicketResponse(ticket, fecha, r.empleadoId(), lineas, subtotal, descuentoTotal,
                subtotal.subtract(descuentoTotal));
    }

    /**
     * Importa ventas historicas. NO toca el stock: los historicos son pasado, el stock es el presente.
     */
    @Transactional
    public Map<String, Object> importarLote(List<VentaLoteRequest> lote) {
        Map<Long, Producto> porId = productoRepository.findAll().stream()
                .collect(Collectors.toMap(Producto::getId, Function.identity()));
        Map<String, Producto> porSku = porId.values().stream()
                .collect(Collectors.toMap(Producto::getSku, Function.identity()));

        List<Object[]> filas = new ArrayList<>(lote.size());
        Set<String> tickets = new HashSet<>();
        LocalDate min = null;
        LocalDate max = null;
        for (int i = 0; i < lote.size(); i++) {
            VentaLoteRequest v = lote.get(i);
            Producto p = v.productoId() != null ? porId.get(v.productoId())
                    : (v.sku() != null ? porSku.get(v.sku()) : null);
            if (p == null) {
                throw new ReglaNegocioException("Fila " + i + ": producto no encontrado",
                        Map.of("fila " + i, "productoId=" + v.productoId() + ", sku=" + v.sku()));
            }
            BigDecimal precio = v.precioUnitario() != null ? v.precioUnitario() : p.getPrecioVenta();
            BigDecimal desc = v.descuentoAplicado() != null ? v.descuentoAplicado() : BigDecimal.ZERO;
            filas.add(new Object[]{v.ticket(), VentaConsultas.ts(v.fechaHora()), p.getId(), v.cantidad(),
                    precio, desc, v.promocionId(), v.empleadoId()});
            tickets.add(v.ticket());
            LocalDate d = v.fechaHora().toLocalDate();
            min = (min == null || d.isBefore(min)) ? d : min;
            max = (max == null || d.isAfter(max)) ? d : max;
        }
        int insertadas = consultas.insertarLote(filas);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("insertadas", insertadas);
        r.put("tickets", tickets.size());
        r.put("desde", min);
        r.put("hasta", max);
        return r;
    }

    @Transactional(readOnly = true)
    public Pagina<VentaResponse> listar(LocalDate desde, LocalDate hasta, Long productoId, Long empleadoId,
                                        Long promocionId, int page, int size) {
        return consultas.listar(desde, hasta, productoId, empleadoId, promocionId, page, size);
    }

    @Transactional(readOnly = true)
    public TicketResponse ticket(String ticket) {
        List<VentaResponse> lineas = consultas.porTicket(ticket);
        if (lineas.isEmpty()) {
            throw new RecursoNoEncontradoException("El ticket " + ticket + " no existe");
        }
        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal desc = BigDecimal.ZERO;
        for (VentaResponse l : lineas) {
            subtotal = subtotal.add(l.precioUnitario().multiply(BigDecimal.valueOf(l.cantidad())));
            desc = desc.add(l.descuentoAplicado());
        }
        VentaResponse primera = lineas.get(0);
        return new TicketResponse(ticket, primera.fechaHora(), primera.empleadoId(), lineas, subtotal, desc,
                subtotal.subtract(desc));
    }

    @Transactional(readOnly = true)
    public List<ResumenVentasItem> resumen(LocalDate desde, LocalDate hasta, String agrupar, Long productoId, String categoria) {
        return consultas.resumen(Fechas.rango(desde, hasta, 30), agrupar, productoId, categoria);
    }

    @Transactional(readOnly = true)
    public RecordsVentas records(LocalDate desde, LocalDate hasta) {
        return consultas.records(Fechas.rango(desde, hasta, 90));
    }
}
