package com.example.store.service;

/**
 * The resource named in the request URL (for example {@code GET /order/{id}}) does not exist. Mapped to 404 by the web
 * layer. Distinct from {@link ReferencedEntityNotFoundException}, which covers a row referenced from a request body and
 * maps to 422.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String entity, Long id) {
        super(entity + " " + id + " does not exist");
    }
}
