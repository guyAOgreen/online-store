package com.example.store.service;

/**
 * A well-formed request referenced a row that does not exist (for example an order whose {@code customer.id} matches no
 * customer). Mapped to 422 by the web layer; 404 is reserved for the resource named in the URL.
 */
public class ReferencedEntityNotFoundException extends RuntimeException {

    public ReferencedEntityNotFoundException(String entity, Long id) {
        super(entity + " " + id + " does not exist");
    }
}
