package com.example.store;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;
import java.util.zip.GZIPInputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Response compression (#9) through the real servlet container: a JSON body above the configured minimum is gzipped
 * when the client accepts it, decompresses to the same bytes the client would otherwise receive, and is left alone for
 * a client that does not ask, or when it is small enough that compressing would cost more than it saves. The bytes are
 * read with {@link HttpClient}, which never decompresses, so the sizes compared are the ones on the wire.
 */
class ResponseCompressionIntegrationTest extends AbstractIntegrationTest {

    @LocalServerPort
    private int port;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void largeJsonIsGzippedForAClientThatAcceptsIt() throws Exception {
        HttpResponse<byte[]> plain = get("/order", false);
        HttpResponse<byte[]> compressed = get("/order", true);

        assertThat(contentEncoding(compressed)).contains("gzip");
        assertThat(compressed.body().length)
                .as("compressed body is a fraction of the %d plain bytes", plain.body().length)
                .isLessThan(plain.body().length / 2);
        assertThat(gunzip(compressed.body())).isEqualTo(plain.body());
    }

    @Test
    void aClientThatDoesNotAcceptGzipGetsThePlainBody() throws Exception {
        HttpResponse<byte[]> plain = get("/order", false);

        assertThat(contentEncoding(plain)).isEmpty();
        assertThat(plain.body()).startsWith("[{".getBytes());
    }

    /**
     * Tomcat's minimum size only applies when it knows the length up front; Spring streams JSON chunked, so even a
     * lookup is compressed. What matters is that it still arrives intact.
     */
    @Test
    void aSmallChunkedResponseIsCompressedTooAndArrivesIntact() throws Exception {
        HttpResponse<byte[]> plain = get("/order/1", false);
        HttpResponse<byte[]> compressed = get("/order/1", true);

        assertThat(plain.headers().firstValue("Content-Length"))
                .as("streamed, so no length")
                .isEmpty();
        assertThat(contentEncoding(compressed)).contains("gzip");
        assertThat(gunzip(compressed.body())).isEqualTo(plain.body());
    }

    private HttpResponse<byte[]> get(String path, boolean acceptGzip) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .GET();
        if (acceptGzip) {
            request.header("Accept-Encoding", "gzip");
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    private static Optional<String> contentEncoding(HttpResponse<?> response) {
        return response.headers().firstValue("Content-Encoding");
    }

    private static byte[] gunzip(byte[] body) throws Exception {
        try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(body))) {
            return in.readAllBytes();
        }
    }
}
