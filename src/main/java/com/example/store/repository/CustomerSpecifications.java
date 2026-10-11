package com.example.store.repository;

import com.example.store.entity.Customer;

import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;

import org.hibernate.query.criteria.HibernateCriteriaBuilder;
import org.springframework.data.jpa.domain.Specification;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * The {@code WHERE} clause of the customer name search (#6). A query is cut into whitespace-separated words and a
 * customer matches when every word is a case-insensitive substring of the name. A word contains no whitespace, so a
 * match necessarily lies inside one word of the name; the words of the query may appear in any order. {@code %},
 * {@code _} and {@code \} in a word are matched literally. Case folding is done by the database ({@code ILIKE} on
 * PostgreSQL) so that one collation defines it, rather than Java and PostgreSQL disagreeing on unusual characters.
 *
 * <p>The predicate is built with Hibernate's {@link HibernateCriteriaBuilder#ilike} rather than the portable
 * {@code lower(name) like lower(:pattern)}: the JPA builder has no case-insensitive {@code like}, and spelling it with
 * {@code CriteriaBuilder#literal} inlines the pattern into the SQL text (safely escaped, but a distinct statement per
 * search), whereas {@code ilike} binds it as a parameter.
 */
public final class CustomerSpecifications {

    /** Unicode white space, so that a non-breaking space separates words just as a plain space does. */
    private static final Pattern WHITESPACE = Pattern.compile("\\s+", Pattern.UNICODE_CHARACTER_CLASS);

    private static final char ESCAPE = '\\';

    private CustomerSpecifications() {}

    /** The words of a query, in order; empty for {@code null} or a query made only of whitespace. */
    public static List<String> words(String query) {
        if (query == null) {
            return List.of();
        }
        return Arrays.stream(WHITESPACE.split(query))
                .filter(word -> !word.isEmpty())
                .toList();
    }

    /** {@code %word%} with the {@code LIKE} metacharacters escaped so the word is matched literally. */
    static String likePattern(String word) {
        StringBuilder pattern = new StringBuilder("%");
        for (char c : word.toCharArray()) {
            if (c == '%' || c == '_' || c == ESCAPE) {
                pattern.append(ESCAPE);
            }
            pattern.append(c);
        }
        return pattern.append('%').toString();
    }

    /**
     * Whether {@code words} can match any stored name. A word containing NUL cannot: PostgreSQL cannot hold NUL in a
     * text value (#4 rejects it on create for the same reason), and binding one as a parameter makes the statement
     * fail, so the caller answers with the empty result instead of asking the database.
     */
    public static boolean canMatch(List<String> words) {
        return words.stream().noneMatch(word -> word.indexOf('\0') >= 0);
    }

    /** Customers whose name contains every one of {@code words}; see the class comment for the exact semantics. */
    public static Specification<Customer> nameContainsEachOf(List<String> words) {
        if (words.isEmpty()) {
            throw new IllegalArgumentException("A name search needs at least one word");
        }
        List<String> patterns =
                words.stream().map(CustomerSpecifications::likePattern).toList();
        return (root, query, builder) -> {
            HibernateCriteriaBuilder hibernate = (HibernateCriteriaBuilder) builder;
            Path<String> name = root.get("name");
            Predicate[] each = patterns.stream()
                    .map(pattern -> hibernate.ilike(name, pattern, ESCAPE))
                    .toArray(Predicate[]::new);
            return builder.and(each);
        };
    }
}
