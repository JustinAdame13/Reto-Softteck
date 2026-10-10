package mx.Tienda_inteligente.pls.Enums;

public class Enums {
    public enum ModeloPrediccion { BASE, IA }

    public enum Temporada { REGRESO_CLASES, NAVIDAD, VERANO, NORMAL }

    public enum CalidadPrediccion { ALTA, MEDIA, BAJA, SIN_HISTORIAL }

    public enum ProveedorIA {
        ANTHROPIC,
        OPENAI,
        GEMINI,
        NINGUNO
    }
}
