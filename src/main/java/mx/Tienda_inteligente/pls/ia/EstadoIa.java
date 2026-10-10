package mx.Tienda_inteligente.pls.ia;

import mx.Tienda_inteligente.pls.Enums.Enums;

import java.util.List;

/**
 * Diagnostico de la IA para GET /api/v1/configuracion/ia/estado.
 * Sirve para saber en dos segundos, en medio de la demo, si lo que falla es la
 * llave, la red o el modelo.
 */
public record EstadoIa(

        /** Lo que dice la tabla configuracion. */
        Enums.ProveedorIA proveedorConfigurado,
        String modeloConfigurado,

        /** true si ese proveedor existe y tiene llave: la IA puede operar. */
        boolean disponible,

        /** Proveedores compilados en la app. */
        List<Enums.ProveedorIA> registrados,

        /** Proveedores con llave, los unicos que el switch acepta. */
        List<Enums.ProveedorIA> conLlave,

        long llamadas,
        long fallos,

        /** De la ultima llamada. null si todavia no se ha hecho ninguna. */
        Boolean ultimaOk,
        Long ultimaLatenciaMs,
        String ultimoError
) {
}
