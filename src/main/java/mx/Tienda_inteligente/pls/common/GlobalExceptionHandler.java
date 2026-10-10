package mx.Tienda_inteligente.pls.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Traduce las excepciones de toda la aplicacion al formato ApiError.
 * Los servicios solo lanzan excepciones; aqui se decide el codigo HTTP.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ResponseEntity<ApiError> noEncontrado(RecursoNoEncontradoException ex) {
        return responder(HttpStatus.NOT_FOUND, "NO_ENCONTRADO", ex.getMessage(), Map.of());
    }

    @ExceptionHandler(ConflictoException.class)
    public ResponseEntity<ApiError> conflicto(ConflictoException ex) {
        return responder(HttpStatus.CONFLICT, "CONFLICTO", ex.getMessage(), Map.of());
    }

    @ExceptionHandler(ReglaNegocioException.class)
    public ResponseEntity<ApiError> reglaNegocio(ReglaNegocioException ex) {
        return responder(HttpStatus.valueOf(422), "REGLA_NEGOCIO", ex.getMessage(), ex.getCampos());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> validacion(MethodArgumentNotValidException ex) {
        Map<String, String> campos = new LinkedHashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            campos.putIfAbsent(fe.getField(), fe.getDefaultMessage());
        }
        ex.getBindingResult().getGlobalErrors()
                .forEach(ge -> campos.putIfAbsent(ge.getObjectName(), ge.getDefaultMessage()));
        return responder(HttpStatus.BAD_REQUEST, "DATOS_INVALIDOS", "Hay campos con errores", campos);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiError> validacionParametros(HandlerMethodValidationException ex) {
        Map<String, String> campos = new LinkedHashMap<>();
        int i = 0;
        for (var error : ex.getAllErrors()) {
            campos.put("error" + (++i), error.getDefaultMessage());
            if (i >= 20) break;
        }
        return responder(HttpStatus.BAD_REQUEST, "DATOS_INVALIDOS", "Hay datos invalidos en la peticion", campos);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> jsonMalFormado(HttpMessageNotReadableException ex) {
        return responder(HttpStatus.BAD_REQUEST, "JSON_INVALIDO",
                "El cuerpo de la peticion no se pudo leer (revisa formato de fechas, enums y numeros)", Map.of());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> tipoInvalido(MethodArgumentTypeMismatchException ex) {
        return responder(HttpStatus.BAD_REQUEST, "PARAMETRO_INVALIDO",
                "Valor invalido para el parametro '" + ex.getName() + "'",
                Map.of(ex.getName(), String.valueOf(ex.getValue())));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiError> parametroFaltante(MissingServletRequestParameterException ex) {
        return responder(HttpStatus.BAD_REQUEST, "PARAMETRO_FALTANTE",
                "Falta el parametro '" + ex.getParameterName() + "'", Map.of());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> integridad(DataIntegrityViolationException ex) {
        log.warn("Violacion de integridad: {}", ex.getMostSpecificCause().getMessage());
        return responder(HttpStatus.CONFLICT, "CONFLICTO",
                "La operacion viola una restriccion de la base de datos: " + ex.getMostSpecificCause().getMessage(),
                Map.of());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> rutaInexistente(NoResourceFoundException ex) {
        return responder(HttpStatus.NOT_FOUND, "NO_ENCONTRADO", "La ruta no existe", Map.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> general(Exception ex) {
        // Excepciones propias de Spring MVC (405, 415, etc.) ya traen su codigo HTTP
        if (ex instanceof ErrorResponse er) {
            HttpStatus status = HttpStatus.valueOf(er.getStatusCode().value());
            return responder(status, status.name(), ex.getMessage(), Map.of());
        }
        log.error("Error no controlado", ex);
        return responder(HttpStatus.INTERNAL_SERVER_ERROR, "ERROR_INTERNO", "Ocurrio un error inesperado", Map.of());
    }

    private ResponseEntity<ApiError> responder(HttpStatus status, String error, String mensaje, Map<String, String> campos) {
        return ResponseEntity.status(status).body(new ApiError(status.value(), error, mensaje, campos));
    }
}
