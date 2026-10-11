package com.example.store;

import com.example.store.dto.CustomerDTO;
import com.example.store.dto.CustomerOrderDTO;
import com.example.store.entity.Customer;
import com.fasterxml.jackson.core.type.TypeReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.net.URI;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code GET /customer?q=…} (#6) against the seeded database. The unit tests prove how a query is cut into words and
 * escaped; these prove what the resulting SQL matches: substrings anywhere inside a word, case-insensitively, with
 * every word of a multiword query required, wildcard characters taken literally, and the same response per customer as
 * the unfiltered listing. Names the seed does not contain (apostrophes, wildcard characters) are inserted with IDs
 * above the seeded range, as the #2 notes prescribe, and removed again by the database cleaner.
 */
class CustomerSearchIntegrationTest extends AbstractIntegrationTest {

    private static final long EXTRA_CUSTOMER_ID = 1_001;

    @ParameterizedTest
    @ValueSource(strings = {"kut", "utc", "tch"})
    void matchesASubstringAtTheStartMiddleOrEndOfAWord(String query) throws Exception {
        assertThat(search(query))
                .extracting(CustomerDTO::getId, CustomerDTO::getName)
                .containsExactly(tuple(6L, "Vicki Kutch"));
    }

    @Test
    void matchesInsideAnyWordOfTheName() throws Exception {
        assertThat(search("ann"))
                .extracting(CustomerDTO::getName)
                .containsExactlyInAnyOrder("Lance Stiedemann Sr.", "Dianne Lemke", "Angelo Pfannerstill");
    }

    @Test
    void ignoresCaseAndSurroundingWhitespace() throws Exception {
        assertThat(search("  kUTCH  ")).extracting(CustomerDTO::getId).containsExactly(6L);
    }

    @Test
    void noMatchIsAnEmptyArray() throws Exception {
        mockMvc.perform(get("/customer").param("q", "zzzz"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void omittedQueryStillListsEveryCustomer() throws Exception {
        assertThat(getList(get("/customer"))).hasSize(100);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void blankQueryListsEveryCustomer(String query) throws Exception {
        assertThat(search(query)).hasSize(100);
    }

    @Test
    void everyWordMustMatchRegardlessOfOrder() throws Exception {
        assertThat(search("glover")).as("the first word alone is not selective").hasSize(3);
        assertThat(search("ed glover")).extracting(CustomerDTO::getName).containsExactly("Ed Glover");
        assertThat(search("glover ed")).extracting(CustomerDTO::getName).containsExactly("Ed Glover");
        assertThat(search("ed glover zz")).isEmpty();
    }

    @Test
    void apostropheMatchesLiterally() throws Exception {
        insertCustomer("Miles O'Brien");

        assertThat(search("o'b")).extracting(CustomerDTO::getName).containsExactly("Miles O'Brien");
    }

    @Test
    void percentSignMatchesLiterallyNotAsAWildcard() throws Exception {
        insertCustomer("Ann%Marie");

        assertThat(search("%")).extracting(CustomerDTO::getName).containsExactly("Ann%Marie");
        assertThat(search("ann%")).extracting(CustomerDTO::getName).containsExactly("Ann%Marie");
    }

    @Test
    void percentSignSurvivesUrlEncoding() throws Exception {
        insertCustomer("Ann%Marie");

        assertThat(getList(get(URI.create("/customer?q=%25"))))
                .extracting(CustomerDTO::getName)
                .containsExactly("Ann%Marie");
    }

    @Test
    void underscoreMatchesLiterallyNotAsAWildcard() throws Exception {
        insertCustomer("Ann_Marie");

        assertThat(search("_")).extracting(CustomerDTO::getName).containsExactly("Ann_Marie");
        assertThat(search("ann_"))
                .as("an unescaped _ would also match Dianne")
                .extracting(CustomerDTO::getName)
                .containsExactly("Ann_Marie");
    }

    @Test
    void backslashMatchesLiterally() throws Exception {
        insertCustomer("Ann\\Marie");

        assertThat(search("\\")).extracting(CustomerDTO::getName).containsExactly("Ann\\Marie");
        assertThat(search("n\\m")).extracting(CustomerDTO::getName).containsExactly("Ann\\Marie");
    }

    @Test
    void nulCharacterMatchesNothingRatherThanFailing() throws Exception {
        assertThat(getList(get(URI.create("/customer?q=%00")))).isEmpty();
        assertThat(search("ann\u0000")).isEmpty();
        assertThat(sqlStatementCount())
                .as("a word no name can contain is answered without a round trip")
                .isZero();
    }

    @Test
    void matchedCustomerIsMappedExactlyAsInTheFullListing() throws Exception {
        CustomerDTO fromListing = getList(get("/customer")).stream()
                .filter(customer -> customer.getId() == 6L)
                .findFirst()
                .orElseThrow();
        Integer orderCount =
                jdbcTemplate.queryForObject("SELECT COUNT(*) FROM \"order\" WHERE customer_id = 6", Integer.class);

        List<CustomerDTO> found = search("kutch");

        assertThat(found).hasSize(1);
        assertThat(found.get(0))
                .usingRecursiveComparison()
                .ignoringCollectionOrder()
                .isEqualTo(fromListing);
        assertThat(found.get(0).getOrders())
                .hasSize(orderCount)
                .extracting(CustomerOrderDTO::getId, CustomerOrderDTO::getDescription)
                .contains(tuple(1L, "Handcrafted Soft Chair"));
    }

    @Test
    void filtersInTheDatabaseRatherThanInMemory() throws Exception {
        search("ann");

        assertThat(entityLoadCount(Customer.class))
                .as("only the matching customers were loaded, not all 100")
                .isEqualTo(3);
    }

    private List<CustomerDTO> search(String query) throws Exception {
        return getList(get("/customer").param("q", query));
    }

    private List<CustomerDTO> getList(MockHttpServletRequestBuilder request) throws Exception {
        String json = mockMvc.perform(request)
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readValue(json, new TypeReference<List<CustomerDTO>>() {});
    }

    private void insertCustomer(String name) {
        jdbcTemplate.update("INSERT INTO customer (id, name) VALUES (?, ?)", EXTRA_CUSTOMER_ID, name);
    }
}
