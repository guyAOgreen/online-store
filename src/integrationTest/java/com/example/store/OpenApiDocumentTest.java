package com.example.store;

import org.junit.jupiter.api.Test;

import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code OpenAPI.yaml} is a valid OpenAPI 3 document (#10): it parses with every reference resolved and no message from
 * the parser. This runs without the application context on purpose. {@link OpenApiContractConfig} loads the same
 * document into every integration test, so a broken document would otherwise surface as a context failure in every
 * test; this one names the problem. It lives in the integration suite only because that is where the parser dependency
 * is; it needs neither Docker nor the database.
 */
class OpenApiDocumentTest {

    @Test
    void documentParsesWithEveryReferenceResolvedAndNoMessages() {
        ParseOptions options = new ParseOptions();
        options.setResolve(true);

        SwaggerParseResult result = new OpenAPIV3Parser()
                .readLocation(OpenApiContractConfig.OPENAPI_DOCUMENT.toUri().toString(), null, options);

        assertThat(result.getMessages()).as("parser messages").isEmpty();
        assertThat(result.getOpenAPI()).isNotNull();
        assertThat(result.getOpenAPI().getPaths()).isNotEmpty();
    }
}
