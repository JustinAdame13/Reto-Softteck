package mx.Tienda_inteligente.pls.Interfaces;

import mx.Tienda_inteligente.pls.Enums.Enums;

/**
 * Un proveedor de LLM. Cada implementacion es un @Component y RegistroLLM los
 * indexa solo, asi que agregar un proveedor nuevo no obliga a tocar nada mas.
 *
 * La IA es OPCIONAL: si el proveedor no tiene api-key o modelo, disponible() = false
 * y el sistema se queda con el algoritmo BASE.
 */
public interface ClienteLLM {

    /** Que proveedor es, para que el registro lo indexe. */
    Enums.ProveedorIA proveedor();

    /** Si tiene credenciales y modelo para operar. */
    boolean disponible();

    /** El modelo que va a usar, para mostrarlo en GET /configuracion/ia/estado. */
    String modelo();

    /**
     * Manda el prompt y devuelve la respuesta en texto plano.
     *
     * @param prefill texto con el que debe empezar la respuesta (por ejemplo "[" para
     *                forzar un arreglo JSON). El proveedor que no lo soporte lo ignora;
     *                el texto devuelto SIEMPRE lo trae al inicio.
     * @throws Exception si la llamada falla. Quien llama (ClienteIa) la atrapa y el
     *                   motor se queda con la prediccion BASE.
     */
    String completar(String sistema, String prompt, int maxTokens, String prefill) throws Exception;
}
