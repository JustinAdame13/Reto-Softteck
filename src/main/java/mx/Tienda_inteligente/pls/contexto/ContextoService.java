package mx.Tienda_inteligente.pls.contexto;

import mx.Tienda_inteligente.pls.common.Fechas;
import mx.Tienda_inteligente.pls.common.RecursoNoEncontradoException;
import mx.Tienda_inteligente.pls.common.ReglaNegocioException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ContextoService {

    private final ContextoRepository repository;

    public ContextoService(ContextoRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<ContextoDto> listar(LocalDate desde, LocalDate hasta) {
        Fechas r = Fechas.rango(desde, hasta, 31);
        return repository.findByFechaBetweenOrderByFecha(r.desde(), r.hasta()).stream()
                .map(ContextoDto::from).toList();
    }

    @Transactional(readOnly = true)
    public ContextoDto obtener(LocalDate fecha) {
        return repository.findByFecha(fecha).map(ContextoDto::from)
                .orElseThrow(() -> new RecursoNoEncontradoException("No hay contexto para " + fecha));
    }

    /**
     * Contexto de cada dia del rango: lo guardado en la tabla y, si falta, lo calculado.
     * Es lo que consumen los motores de prediccion y promociones.
     */
    @Transactional(readOnly = true)
    public Map<LocalDate, ContextoDto> mapa(LocalDate desde, LocalDate hasta) {
        Map<LocalDate, ContextoDto> guardados = repository.findByFechaBetweenOrderByFecha(desde, hasta).stream()
                .collect(Collectors.toMap(Contexto::getFecha, ContextoDto::from));
        Map<LocalDate, ContextoDto> resultado = new HashMap<>();
        for (LocalDate f = desde; !f.isAfter(hasta); f = f.plusDays(1)) {
            resultado.put(f, guardados.getOrDefault(f, CalendarioMexico.calcular(f)));
        }
        return resultado;
    }

    /** Crea o actualiza (upsert) el dia indicado. */
    @Transactional
    public ContextoDto guardar(LocalDate fecha, ContextoDto dto) {
        Contexto c = repository.findByFecha(fecha).orElseGet(() -> {
            Contexto n = new Contexto();
            n.setFecha(fecha);
            return n;
        });
        aplicar(c, dto);
        return ContextoDto.from(repository.save(c));
    }

    @Transactional
    public List<ContextoDto> guardarLote(List<ContextoDto> lote) {
        if (lote.stream().anyMatch(d -> d.fecha() == null)) {
            throw new ReglaNegocioException("Cada elemento del lote necesita 'fecha'");
        }
        LocalDate min = lote.stream().map(ContextoDto::fecha).min(LocalDate::compareTo).orElseThrow();
        LocalDate max = lote.stream().map(ContextoDto::fecha).max(LocalDate::compareTo).orElseThrow();
        Map<LocalDate, Contexto> existentes = repository.findByFechaBetweenOrderByFecha(min, max).stream()
                .collect(Collectors.toMap(Contexto::getFecha, Function.identity()));
        List<Contexto> aGuardar = new ArrayList<>();
        for (ContextoDto d : lote) {
            Contexto c = existentes.computeIfAbsent(d.fecha(), f -> {
                Contexto n = new Contexto();
                n.setFecha(f);
                return n;
            });
            aplicar(c, d);
            if (!aGuardar.contains(c)) {
                aGuardar.add(c);
            }
        }
        return repository.saveAll(aGuardar).stream().map(ContextoDto::from).toList();
    }

    /**
     * Llena todo el anio con quincenas, festivos, eventos y temporadas.
     * Respeta clima y temperatura si ya estaban capturados.
     */
    @Transactional
    public List<ContextoDto> generar(int anio) {
        if (anio < 2000 || anio > 2100) {
            throw new ReglaNegocioException("Anio fuera de rango");
        }
        LocalDate desde = LocalDate.of(anio, 1, 1);
        LocalDate hasta = LocalDate.of(anio, 12, 31);
        Map<LocalDate, Contexto> existentes = repository.findByFechaBetweenOrderByFecha(desde, hasta).stream()
                .collect(Collectors.toMap(Contexto::getFecha, Function.identity()));
        List<Contexto> aGuardar = new ArrayList<>();
        for (LocalDate f = desde; !f.isAfter(hasta); f = f.plusDays(1)) {
            ContextoDto calc = CalendarioMexico.calcular(f);
            Contexto c = existentes.get(f);
            if (c == null) {
                c = new Contexto();
                c.setFecha(f);
            }
            c.setEsFestivo(calc.esFestivo());
            c.setNombreEvento(calc.nombreEvento());
            c.setEsQuincena(calc.esQuincena());
            c.setTemporada(calc.temporada());
            aGuardar.add(c);
        }
        return repository.saveAll(aGuardar).stream().map(ContextoDto::from).toList();
    }

    private void aplicar(Contexto c, ContextoDto d) {
        ContextoDto calc = CalendarioMexico.calcular(c.getFecha());
        c.setEsFestivo(d.esFestivo() != null ? d.esFestivo() : calc.esFestivo());
        c.setNombreEvento(d.nombreEvento() != null ? d.nombreEvento() : calc.nombreEvento());
        c.setEsQuincena(d.esQuincena() != null ? d.esQuincena() : calc.esQuincena());
        c.setTemporada(d.temporada() != null ? d.temporada() : calc.temporada());
        c.setClima(d.clima());
        c.setTemperatura(d.temperatura());
    }
}
