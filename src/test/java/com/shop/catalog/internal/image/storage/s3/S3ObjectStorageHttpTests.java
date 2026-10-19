package com.shop.catalog.internal.image.storage.s3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shop.catalog.internal.image.storage.ObjectStorageException;
import com.shop.catalog.internal.image.storage.ObjectStorageUpload;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Status;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/** SDK wire-contract tests against a loopback server, not evidence of AWS IAM or S3 availability. */
class S3ObjectStorageHttpTests {

    private static final String KEY = "catalog/products/qa/image.png";
    private static final String BUCKET = "shop-qa-images";
    private final Map<String, byte[]> objects = new ConcurrentHashMap<>();
    private final AtomicInteger requests = new AtomicInteger();
    private final AtomicInteger responseStatus = new AtomicInteger();
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private final AtomicReference<String> sessionToken = new AtomicReference<>();
    private final AtomicReference<String> contentType = new AtomicReference<>();
    private final AtomicReference<String> condition = new AtomicReference<>();
    private HttpServer server;
    private ExecutorService executor;
    private S3Client client;
    private S3Presigner presigner;
    private S3ObjectStorage storage;

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        executor = Executors.newVirtualThreadPerTaskExecutor();
        server.setExecutor(executor);
        server.createContext("/", this::respond);
        server.start();
        var props = new S3ObjectStorageProperties();
        props.setRegion("ap-southeast-1");
        props.setBucket(BUCKET);
        props.setConnectTimeout(Duration.ofSeconds(1));
        props.setReadTimeout(Duration.ofSeconds(1));
        props.setCallTimeout(Duration.ofSeconds(1));
        props.validate();
        var credentials = StaticCredentialsProvider.create(
                AwsSessionCredentials.create("test-http-access", "test-http-secret", "test-http-token"));
        URI endpoint = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
        client = S3ObjectStorageConfiguration.clientBuilder(props, credentials)
                .endpointOverride(endpoint)
                .forcePathStyle(true)
                .build();
        presigner = S3Presigner.builder()
                .region(Region.AP_SOUTHEAST_1)
                .credentialsProvider(credentials)
                .endpointOverride(endpoint)
                .serviceConfiguration(software.amazon.awssdk.services.s3.S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .build())
                .build();
        var configuration = new S3ObjectStorageConfiguration();
        storage = new S3ObjectStorage(
                client,
                presigner,
                props,
                configuration.catalogS3CircuitBreaker(configuration.catalogS3CircuitBreakerRegistry(props)));
    }

    @AfterEach
    void stop() {
        if (client != null) client.close();
        if (presigner != null) presigner.close();
        if (server != null) server.stop(0);
        if (executor != null) executor.shutdownNow();
    }

    @Test
    void sdkUploadsSignsReadsAndDeletesWithoutOverwriting() throws Exception {
        storage.initialize();
        byte[] content = {5, 6, 7};
        var upload = new ObjectStorageUpload(KEY, "image/png", content);
        storage.store(upload);
        assertThat(objects.get("/" + BUCKET + "/" + KEY)).containsExactly(content);
        assertThat(contentType.get()).isEqualTo("image/png");
        assertThat(condition.get()).isEqualTo("*");
        assertThat(authorization.get()).startsWith("AWS4-HMAC-SHA256 ");
        assertThat(sessionToken.get()).isEqualTo("test-http-token");
        int beforeDuplicate = requests.get();
        assertThatThrownBy(() -> storage.store(upload)).isInstanceOf(ObjectStorageException.class);
        assertThat(requests.get()).isEqualTo(beforeDuplicate + 1);
        assertThat(objects.get("/" + BUCKET + "/" + KEY)).containsExactly(content);
        URI url = storage.createReadUrl(KEY).orElseThrow();
        assertThat(url.getRawQuery()).contains("X-Amz-Expires=900");
        var response = HttpClient.newHttpClient()
                .send(HttpRequest.newBuilder(url).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).containsExactly(content);
        storage.delete(KEY);
        storage.delete(KEY);
        assertThat(objects).isEmpty();
    }

    @Test
    void sdkDoesNotRetryProviderErrorsOrExposeResponseBodies() {
        for (int status : new int[] {403, 404, 500, 503}) {
            responseStatus.set(status);
            int before = requests.get();
            assertThatThrownBy(() -> storage.store(new ObjectStorageUpload(KEY, "image/png", new byte[] {1})))
                    .isInstanceOf(ObjectStorageException.class)
                    .hasNoCause()
                    .hasMessageNotContaining("sensitive-provider-body");
            assertThat(requests.get()).isEqualTo(before + 1);
        }
        assertThat(objects).isEmpty();
    }

    @Test
    void sdkBoundsSlowCallsAndDoesNotRetryAfterTimeout() {
        responseStatus.set(-1);
        long started = System.nanoTime();
        assertThatThrownBy(storage::initialize).isInstanceOf(ObjectStorageException.class);
        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(4));
        assertThat(requests.get()).isEqualTo(1);
        assertThat(storage.health().getStatus()).isEqualTo(Status.DOWN);
    }

    private void respond(HttpExchange exchange) throws IOException {
        requests.incrementAndGet();
        int status = responseStatus.get();
        if (status == -1) {
            try {
                Thread.sleep(2000);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            exchange.close();
            return;
        }
        if (status > 0) {
            send(
                    exchange,
                    status,
                    "<Error><Code>AccessDenied</Code><Message>sensitive-provider-body</Message></Error>"
                            .getBytes(StandardCharsets.UTF_8));
            return;
        }
        String path = exchange.getRequestURI().getPath();
        switch (exchange.getRequestMethod()) {
            case "HEAD" -> send(exchange, 200, new byte[0]);
            case "PUT" -> {
                authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
                sessionToken.set(exchange.getRequestHeaders().getFirst("X-Amz-Security-Token"));
                contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
                condition.set(exchange.getRequestHeaders().getFirst("If-None-Match"));
                byte[] body = exchange.getRequestBody().readAllBytes();
                String encoding = exchange.getRequestHeaders().getFirst("Content-Encoding");
                if (encoding != null && encoding.contains("aws-chunked")) {
                    body = decodeAwsChunks(body);
                }
                if (objects.putIfAbsent(path, body) != null) {
                    send(
                            exchange,
                            412,
                            "<Error><Code>PreconditionFailed</Code></Error>".getBytes(StandardCharsets.UTF_8));
                } else {
                    exchange.getResponseHeaders().add("ETag", "\"test-etag\"");
                    send(exchange, 200, new byte[0]);
                }
            }
            case "GET" ->
                send(exchange, objects.containsKey(path) ? 200 : 404, objects.getOrDefault(path, new byte[0]));
            case "DELETE" -> {
                objects.remove(path);
                send(exchange, 204, new byte[0]);
            }
            default -> send(exchange, 405, new byte[0]);
        }
    }

    private void send(HttpExchange exchange, int status, byte[] body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/xml");
        boolean empty = body.length == 0 || exchange.getRequestMethod().equals("HEAD");
        exchange.sendResponseHeaders(status, empty ? -1 : body.length);
        if (!empty) exchange.getResponseBody().write(body);
        exchange.close();
    }

    private byte[] decodeAwsChunks(byte[] wire) throws IOException {
        var decoded = new ByteArrayOutputStream();
        int cursor = 0;
        while (cursor < wire.length) {
            int end = cursor;
            while (end + 1 < wire.length && !(wire[end] == '\r' && wire[end + 1] == '\n')) end++;
            String header = new String(wire, cursor, end - cursor, StandardCharsets.US_ASCII);
            int length = Integer.parseInt(header.split(";", 2)[0], 16);
            if (length == 0) return decoded.toByteArray();
            cursor = end + 2;
            decoded.write(wire, cursor, length);
            cursor += length + 2;
        }
        throw new IOException("Incomplete SDK fixture chunk stream");
    }
}
