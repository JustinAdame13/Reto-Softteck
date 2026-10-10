package mx.Tienda_inteligente.pls.Interfaces;


public interface ClienteLLM {

    /**
     * Manda el prompt y devuelve la respuesta en texto plano.
     * Si algo falla lanza excepcion: el motor IA la atrapa
     * y se queda con la prediccion BASE.
     */
    String completar(String prompt) throws Exception;
}