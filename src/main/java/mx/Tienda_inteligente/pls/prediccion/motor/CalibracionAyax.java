package mx.Tienda_inteligente.pls.prediccion.motor;

/**
 * Como quedo calibrado Ayax para un producto. Se regresa en POST /predicciones/generar
 * (campo calibracionAyax) para explicarle al jurado por que cada producto usa lo que usa.
 *
 * @param metodo                     "Ayax-Estacional" o "Ayax-Intermitente" (Croston-SBA)
 * @param alfa                       que tan rapido se adapta a cambios recientes (0.05 lento ... 0.35 rapido)
 * @param shrink                     que tanto se encogen los factores hacia 1 (null en Croston)
 * @param configuracionesProbadas    cuantas configuraciones se validaron (0 = no habia historial suficiente)
 * @param errorValidacion            WAPE % de la ganadora en las 4 semanas apartadas (null si no se calibro)
 * @param sesgoValidacion            sesgo % de la ganadora: + pronostico de mas, - de menos
 * @param errorConfiguracionDefecto  WAPE % que habria tenido la configuracion fija de antes, para comparar
 */
public record CalibracionAyax(
        String metodo,
        Double alfa,
        Double shrink,
        int configuracionesProbadas,
        Double errorValidacion,
        Double sesgoValidacion,
        Double errorConfiguracionDefecto
) {

    public static CalibracionAyax sinHistorial() {
        return new CalibracionAyax("Sin historial", null, null, 0, null, null, null);
    }
}
