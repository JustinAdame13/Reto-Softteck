package mx.Tienda_inteligente.pls.common;

import java.util.List;

/** Respuesta paginada estable (no depende del formato interno de Spring Data). */
public record Pagina<T>(List<T> contenido, int pagina, int tamano, long totalElementos, int totalPaginas) {

    public static <T> Pagina<T> of(List<T> contenido, int pagina, int tamano, long total) {
        int paginas = tamano == 0 ? 0 : (int) Math.ceil((double) total / tamano);
        return new Pagina<>(contenido, pagina, tamano, total, paginas);
    }
}
