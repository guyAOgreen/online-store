package com.example.store.controller;

import com.example.store.service.ReferencedEntityNotFoundException;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Turns every client error into RFC 9457 {@code application/problem+json}. Extending
 * {@link ResponseEntityExceptionHandler} replaces Spring Boot's auto-configured handler, so the standard exceptions
 * (unsupported media type, type mismatch on a path variable, and so on) already come out in the same shape. An
 * {@code errors} array of {@code {field, message}} is added whenever a field can be named; malformed or missing bodies
 * get a {@code detail} only.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    static final String ERRORS = "errors";
    static final String MALFORMED_BODY = "Malformed or missing request body";

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<Map<String, String>> errors = ex.getBindingResult().getFieldErrors().stream()
                .sorted(Comparator.comparing(FieldError::getField))
                .map(error -> fieldError(
                        error.getField(), Objects.requireNonNullElse(error.getDefaultMessage(), "invalid value")))
                .toList();
        ProblemDetail body = ex.getBody();
        body.setProperty(ERRORS, errors);
        return handleExceptionInternal(ex, body, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail body = ProblemDetail.forStatus(status);
        // A syntax error is checked first: Jackson may wrap one that occurs mid-object in a mapping exception that
        // carries a field path, and a truncated body is not an invalid value for the field it stopped at.
        boolean malformedSyntax = findCause(ex, JsonParseException.class) != null;
        MismatchedInputException mapping = findCause(ex, MismatchedInputException.class);
        if (!malformedSyntax && mapping != null && !mapping.getPath().isEmpty()) {
            String field = fieldPath(mapping);
            if (mapping instanceof UnrecognizedPropertyException) {
                body.setDetail("Unknown field '" + field + "'");
                body.setProperty(ERRORS, List.of(fieldError(field, "unknown field")));
            } else {
                body.setDetail("Invalid value for field '" + field + "'");
                body.setProperty(ERRORS, List.of(fieldError(field, "invalid value")));
            }
        } else {
            body.setDetail(MALFORMED_BODY);
        }
        return handleExceptionInternal(ex, body, headers, status, request);
    }

    /** The body was well formed but named a row that does not exist. 404 stays reserved for the resource in the URL. */
    @ExceptionHandler(ReferencedEntityNotFoundException.class)
    ProblemDetail handleReferencedEntityNotFound(ReferencedEntityNotFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
    }

    private static Map<String, String> fieldError(String field, String message) {
        return Map.of("field", field, "message", message);
    }

    /**
     * Dotted path of the offending property, for example {@code customer.id}; array positions appear as {@code [n]}.
     */
    private static String fieldPath(JsonMappingException ex) {
        return ex.getPath().stream()
                .map(reference ->
                        reference.getFieldName() != null ? reference.getFieldName() : "[" + reference.getIndex() + "]")
                .collect(Collectors.joining("."))
                .replace(".[", "[");
    }

    private static <T extends Throwable> T findCause(Throwable root, Class<T> type) {
        for (Throwable current = root; current != null; current = current.getCause()) {
            if (type.isInstance(current)) {
                return type.cast(current);
            }
            if (current.getCause() == current) {
                break;
            }
        }
        return null;
    }
}
