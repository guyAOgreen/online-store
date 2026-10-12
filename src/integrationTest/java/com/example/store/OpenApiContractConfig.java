package com.example.store;

import com.atlassian.oai.validator.OpenApiInteractionValidator;
import com.atlassian.oai.validator.mockmvc.OpenApiValidationMatchers;
import com.atlassian.oai.validator.report.LevelResolver;
import com.atlassian.oai.validator.report.ValidationReport;
import com.atlassian.oai.validator.whitelist.ValidationErrorsWhitelist;

import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcBuilderCustomizer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import java.nio.file.Path;

import static com.atlassian.oai.validator.whitelist.rule.WhitelistRules.allOf;
import static com.atlassian.oai.validator.whitelist.rule.WhitelistRules.messageContainsSubstring;
import static com.atlassian.oai.validator.whitelist.rule.WhitelistRules.messageHasKey;

/**
 * Holds {@code OpenAPI.yaml} to the implementation (#10). Every exchange the integration suite sends through
 * {@link org.springframework.test.web.servlet.MockMvc} is checked against the document: the path and method must be
 * documented, the status code must be listed for that operation, and the response body must match the schema for it, so
 * a response that gains or loses a field, or an endpoint that gains a status code, fails the suite until the document
 * says so.
 *
 * <p>Request bodies, parameters and content types are deliberately not validated: the 4xx tests send malformed, unknown
 * and over-length input and the wrong media type on purpose, and what matters there is that the resulting error is
 * documented. The actuator paths are operational rather than part of the API and are the one exception to "the path
 * must be documented".
 */
@TestConfiguration(proxyBeanMethods = false)
public class OpenApiContractConfig {

    /** Gradle runs the suite from the project directory, where the document lives next to the README. */
    static final Path OPENAPI_DOCUMENT = Path.of("OpenAPI.yaml").toAbsolutePath();

    @Bean
    OpenApiInteractionValidator openApiValidator() {
        return OpenApiInteractionValidator.createForSpecificationUrl(
                        OPENAPI_DOCUMENT.toUri().toString())
                .withLevelResolver(LevelResolver.create()
                        .withLevel("validation.request.body", ValidationReport.Level.IGNORE)
                        .withLevel("validation.request.parameter", ValidationReport.Level.IGNORE)
                        .withLevel("validation.request.contentType", ValidationReport.Level.IGNORE)
                        .build())
                // The path rules look at the matched operation, and an unknown path has none, so the request path is
                // taken from the message text instead.
                .withWhitelist(ValidationErrorsWhitelist.create()
                        .withRule(
                                "Actuator endpoints are operational, not part of the documented API",
                                allOf(
                                        messageHasKey("validation.request.path.missing"),
                                        messageContainsSubstring("'/actuator/"))))
                .build();
    }

    @Bean
    MockMvcBuilderCustomizer validateEveryExchangeAgainstOpenApi(OpenApiInteractionValidator validator) {
        return builder ->
                builder.alwaysExpect(OpenApiValidationMatchers.openApi().isValid(validator));
    }
}
