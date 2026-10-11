package com.example.store.service;

import java.util.Collection;
import java.util.stream.Collectors;

/**
 * A well-formed request referenced a row that does not exist (for example an order whose {@code customer.id} matches no
 * customer, or whose {@code products} name IDs no product has). Mapped to 422 by the web layer; 404 is reserved for the
 * resource named in the URL.
 */
public class ReferencedEntityNotFoundException extends RuntimeException {

    public ReferencedEntityNotFoundException(String entity, Long id) {
        super(entity + " " + id + " does not exist");
    }

    /** Names every missing ID in ascending order, so the client can fix the whole request in one round trip. */
    public ReferencedEntityNotFoundException(String entity, Collection<Long> ids) {
        super(
                ids.size() == 1
                        ? entity + " " + ids.iterator().next() + " does not exist"
                        : entity + "s "
                                + ids.stream().sorted().map(String::valueOf).collect(Collectors.joining(", "))
                                + " do not exist");
    }
}
