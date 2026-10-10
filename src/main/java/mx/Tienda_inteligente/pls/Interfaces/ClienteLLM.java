package mx.Tienda_inteligente.pls.Interfaces;

import mx.Tienda_inteligente.pls.Enums.Enums;

/**
 * Un proveedor de LLM. Cada implementacion es un @Component y RegistroLLM los
 * indexa solo, asi que agregar un proveedor nuevo no obliga a tocar nada mas.
 *
 * La IA es OPCIONAL: si el proveedor no tiene api-key o modelo, disponible() = false
 * y el sistema se queda con Ayax sin IA (modelo BASE).
 */
public interface ClienteLLM {

    /** Que proveedor es, para que el registro lo indexe. */
    Enums.ProveedorIA proveedor();

    /** Si tiene credenciales y modelo para operar. */
    boolean disponible();

    /** El modelo por defecto (el del .env, o el del codigo si el .env no trae). */
    String modelo();

    /**
     * Manda el prompt con el modelo por defecto y devuelve la respuesta en texto plano.
     *
     * @param prefill texto con el que debe empezar la respuesta (por ejemplo "[" para
     *                forzar un arreglo JSON). El proveedor que no lo soporte lo ignora;
     *                el texto devuelto SIEMPRE lo trae al inicio.
     * @throws Exception si la llamada falla. Quien llama (ClienteIa) la atrapa y el
     *                   motor se queda con la prediccion BASE.
     */
    default String completar(String sistema, String prompt, int maxTokens, String prefill) throws Exception {
        return completar(null, sistema, prompt, maxTokens, prefill);
    }

    /**
     * Igual que el anterior, pero con un modelo especifico (el que el gerente eligio con
     * PATCH /configuracion/ia). Si modelo es null o vacio se usa {@link #modelo()}.
     */
    String completar(String modelo, String sistema, String prompt, int maxTokens, String prefill) throws Exception;
}
