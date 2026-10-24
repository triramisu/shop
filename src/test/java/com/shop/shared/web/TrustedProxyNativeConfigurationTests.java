package com.shop.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.ServletException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.valves.RemoteIpValve;
import org.apache.catalina.valves.ValveBase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.web.WebProperties;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.tomcat.autoconfigure.TomcatServerProperties;
import org.springframework.boot.tomcat.autoconfigure.TomcatWebServerFactoryCustomizer;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.autoconfigure.ServerProperties;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

/** Real Boot binding and Tomcat valve; no AWS, database, public endpoint or payment. */
class TrustedProxyNativeConfigurationTests {

    private record Observed(String ip, String host, int port, boolean secure) {}

    private RemoteIpValve configuredValve() throws IOException {
        Map<String, Object> variables = new LinkedHashMap<>();
        try (var input = getClass().getResourceAsStream("/contracts/trusted-proxy.env")) {
            assertThat(input).isNotNull();
            for (String line : new String(input.readAllBytes(), StandardCharsets.UTF_8).split("\\R")) {
                if (!line.isEmpty()) {
                    int delimiter = line.indexOf('=');
                    variables.put(line.substring(0, delimiter), line.substring(delimiter + 1));
                }
            }
        }
        var environment = new StandardEnvironment();
        environment
                .getPropertySources()
                .replace(
                        StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                        new SystemEnvironmentPropertySource(
                                StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, variables));
        var binder = Binder.get(environment);
        var server = binder.bind("server", Bindable.of(ServerProperties.class)).get();
        var tomcat = binder.bind("server.tomcat", Bindable.of(TomcatServerProperties.class))
                .get();
        assertThat(server.getForwardHeadersStrategy()).isEqualTo(ServerProperties.ForwardHeadersStrategy.NATIVE);
        var factory = new TomcatServletWebServerFactory();
        new TomcatWebServerFactoryCustomizer(environment, server, tomcat, new WebProperties()).customize(factory);
        return factory.getEngineValves().stream()
                .filter(RemoteIpValve.class::isInstance)
                .map(RemoteIpValve.class::cast)
                .findFirst()
                .orElseThrow();
    }

    private Observed invoke(String peer, Map<String, String> headers) throws IOException, ServletException {
        var valve = configuredValve();
        var coyote = new org.apache.coyote.Request();
        coyote.scheme().setString("http");
        coyote.serverName().setString("origin.example.com");
        coyote.setServerPort(8080);
        var request = new Request(new Connector(), coyote);
        request.setRemoteAddr(peer);
        request.setRemoteHost(peer);
        headers.forEach((key, value) -> coyote.getMimeHeaders().addValue(key).setString(value));
        var observed = new AtomicReference<Observed>();
        valve.setNext(new ValveBase() {
            @Override
            public void invoke(Request req, Response res) {
                observed.set(
                        new Observed(req.getRemoteAddr(), req.getServerName(), req.getServerPort(), req.isSecure()));
            }
        });
        valve.invoke(request, new Response(new org.apache.coyote.Response()));
        return observed.get();
    }

    @Test
    void bindsExactDeploymentHeaderAndNarrowPeerTrust() throws IOException {
        var valve = configuredValve();
        assertThat(valve.getRemoteIpHeader()).isEqualTo("x-shop-client-ip");
        assertThat(valve.getInternalProxies()).isEqualTo("^10[.]0[.](1|3)[.][0-9]{1,3}$");
        assertThat(valve.getTrustedProxies()).isEqualTo("^$");
        assertThat(valve.getHostHeader()).isEmpty();
        assertThat(valve.getPortHeader()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"10.0.1.12", "10.0.3.45"})
    void trustedAlbPeerUsesEdgeIpAndIgnoresSpoofedForwardedHostPort(String peer) throws Exception {
        var result = invoke(
                peer,
                Map.of(
                        "x-shop-client-ip",
                        "203.0.113.42",
                        "x-forwarded-for",
                        "198.51.100.99",
                        "forwarded",
                        "for=198.51.100.88;host=evil.example",
                        "x-forwarded-host",
                        "evil.example",
                        "x-forwarded-port",
                        "4444",
                        "x-forwarded-proto",
                        "https"));
        assertThat(result).isEqualTo(new Observed("203.0.113.42", "origin.example.com", 443, true));
    }

    @ParameterizedTest
    @ValueSource(strings = {"10.0.2.12", "10.0.4.12", "10.0.5.12", "172.17.0.1", "127.0.0.1", "198.51.100.9", "::1"})
    void untrustedPeerCannotSetClientIpHostOrScheme(String peer) throws Exception {
        var result = invoke(
                peer,
                Map.of(
                        "x-shop-client-ip",
                        "203.0.113.42",
                        "x-forwarded-for",
                        "198.51.100.99",
                        "x-forwarded-proto",
                        "https",
                        "x-forwarded-host",
                        "evil.example",
                        "x-forwarded-port",
                        "4444"));
        assertThat(result).isEqualTo(new Observed(peer, "origin.example.com", 8080, false));
    }

    @Test
    void trustedPeerWithoutCanonicalHeaderNeverUsesXff() throws Exception {
        assertThat(invoke("10.0.1.12", Map.of("x-forwarded-for", "198.51.100.99"))
                        .ip())
                .isEqualTo("10.0.1.12");
    }

    @Test
    void trustedPeerSupportsLiteralIpv6Viewer() throws Exception {
        assertThat(invoke("10.0.3.45", Map.of("x-shop-client-ip", "2001:db8::42"))
                        .ip())
                .isEqualTo("2001:db8::42");
    }
}
