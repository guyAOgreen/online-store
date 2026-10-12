package com.example.store.dto;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * No product ID may appear more than once in a list of {@link ProductReference}s. Quantities are outside the model, so
 * a repeated product has no representable meaning; rejecting it (400) rather than collapsing it keeps a client's
 * probable mistake visible, in line with the project rejecting unknown fields and duplicate JSON keys (#4). Null
 * elements and null IDs are ignored here because the element constraints already report them.
 */
@Documented
// FIELD is what makes the constraint reach a record: javac copies a record-component annotation onto the backing
// field only when the annotation's target allows it, and the field is where the validator looks.
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = DistinctProducts.Validator.class)
public @interface DistinctProducts {

    String message() default "must not contain the same product more than once";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<DistinctProducts, List<ProductReference>> {
        @Override
        public boolean isValid(List<ProductReference> products, ConstraintValidatorContext context) {
            if (products == null) {
                return true;
            }
            Set<Long> seen = new HashSet<>();
            for (ProductReference product : products) {
                if (product != null && product.id() != null && !seen.add(product.id())) {
                    return false;
                }
            }
            return true;
        }
    }
}
