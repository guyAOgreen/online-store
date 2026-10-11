package com.example.store.repository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * The pure parts of the customer name search: how a query is cut into words and how a word becomes a {@code LIKE}
 * pattern. What the resulting specification matches is proven against PostgreSQL in the integration suite.
 */
class CustomerSpecificationsTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t\n", "  "})
    void blankQueryHasNoWords(String query) {
        assertThat(CustomerSpecifications.words(query)).isEmpty();
    }

    @Test
    void surroundingWhitespaceIsDropped() {
        assertThat(CustomerSpecifications.words("  ann\t")).containsExactly("ann");
    }

    @Test
    void wordsAreSplitOnAnyRunOfWhitespace() {
        assertThat(CustomerSpecifications.words("jo \t\n sm")).containsExactly("jo", "sm");
    }

    @Test
    void nonAsciiSpacesSeparateWordsToo() {
        assertThat(CustomerSpecifications.words(" jo sm ")).containsExactly("jo", "sm");
    }

    @Test
    void wordsKeepTheirCase() {
        // Case folding happens in the database so that one collation defines it.
        assertThat(CustomerSpecifications.words("Ann")).containsExactly("Ann");
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            quoteCharacter = '"',
            value = {
                "ann      | %ann%",
                "50%      | %50\\%%",
                "a_b      | %a\\_b%",
                "a\\b     | %a\\\\b%",
                "o'brien  | %o'brien%",
                "%_\\     | %\\%\\_\\\\%",
            })
    void likePatternEscapesWildcardsAndWrapsInPercent(String word, String expected) {
        assertThat(CustomerSpecifications.likePattern(word)).isEqualTo(expected);
    }

    @Test
    void specificationNeedsAtLeastOneWord() {
        assertThatIllegalArgumentException().isThrownBy(() -> CustomerSpecifications.nameContainsEachOf(List.of()));
    }
}
