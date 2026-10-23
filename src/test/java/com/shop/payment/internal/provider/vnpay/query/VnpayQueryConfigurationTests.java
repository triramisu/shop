package com.shop.payment.internal.provider.vnpay.query;

import static com.shop.payment.internal.provider.vnpay.query.VnpayQueryFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shop.payment.internal.provider.vnpay.VnpayProperties;
import com.shop.payment.internal.provider.vnpay.VnpaySignature;
import com.shop.payment.provider.PaymentStatusQueryOperations;
import java.time.Duration;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tools.jackson.databind.ObjectMapper;

class VnpayQueryConfigurationTests {
    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withUserConfiguration(VnpayQueryConfiguration.class);

    @Test
    void disabledQueryAndOtherProvidersNeverRequireQueryIpOrMerchantBeans() {
        for (String type : new String[] {"none", "fake", "stripe"}) {
            runner.withPropertyValues("app.payment.provider.type=" + type, "app.payment.vnpay.query.enabled=true")
                    .run(context -> assertThat(context).doesNotHaveBean(PaymentStatusQueryOperations.class));
        }
        runner.withPropertyValues("app.payment.provider.type=vnpay")
                .run(context -> assertThat(context).doesNotHaveBean(PaymentStatusQueryOperations.class));
        runner.withPropertyValues(
                        "app.payment.provider.type=vnpay",
                        "app.payment.vnpay.query.enabled=false",
                        "app.payment.vnpay.query.server-ip=bad")
                .run(context -> assertThat(context).doesNotHaveBean(PaymentStatusQueryOperations.class));
    }

    @Test
    void optInBuildsBoundedClientAndSeparateQueryPortWithoutPerformingNetworkCalls() {
        enabled()
                .withPropertyValues("app.payment.vnpay.query.server-ip=203.0.113.10")
                .run(context -> {
                    assertThat(context).hasSingleBean(PaymentStatusQueryOperations.class);
                    var client = context.getBean("vnpayQueryHttpClient", OkHttpClient.class);
                    assertThat(client.connectTimeoutMillis()).isEqualTo(2000);
                    assertThat(client.readTimeoutMillis()).isEqualTo(3000);
                    assertThat(client.callTimeoutMillis()).isEqualTo(5000);
                    assertThat(client.followRedirects()).isFalse();
                    assertThat(client.followSslRedirects()).isFalse();
                    assertThat(client.retryOnConnectionFailure()).isFalse();
                    assertThat(context.getBean(PaymentStatusQueryOperations.class)
                                    .providerCode())
                            .isEqualTo("VNPAY");
                });
        enabled().run(context -> assertThat(context).hasFailed());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "",
                "256.1.1.1",
                "1.1.1",
                "01.1.1.1",
                "::1",
                "https://example.com",
                "1.1.1.1|x",
                "1.1.1.1 ",
                "a.b.c.d"
            })
    void rejectsInvalidServerIp(String ip) {
        var properties = new VnpayQueryProperties();
        properties.setServerIp(ip);
        assertThatThrownBy(() -> VnpayQueryProperties.validate(properties)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsNullOrUnboundedAndInconsistentTimeouts() {
        for (Duration duration : new Duration[] {null, Duration.ZERO, Duration.ofMillis(99), Duration.ofSeconds(11)}) {
            var properties = validProperties();
            properties.setConnectTimeout(duration);
            var invalidConnect = properties;
            assertThatThrownBy(() -> VnpayQueryProperties.validate(invalidConnect))
                    .isInstanceOf(IllegalStateException.class);
            properties = validProperties();
            properties.setReadTimeout(duration);
            var invalidRead = properties;
            assertThatThrownBy(() -> VnpayQueryProperties.validate(invalidRead))
                    .isInstanceOf(IllegalStateException.class);
            properties = validProperties();
            properties.setCallTimeout(duration);
            var invalidCall = properties;
            assertThatThrownBy(() -> VnpayQueryProperties.validate(invalidCall))
                    .isInstanceOf(IllegalStateException.class);
        }
        var properties = validProperties();
        properties.setServerIp(null);
        var missingIp = properties;
        assertThatThrownBy(() -> VnpayQueryProperties.validate(missingIp)).isInstanceOf(IllegalStateException.class);
        properties = validProperties();
        properties.setCallTimeout(Duration.ofSeconds(1));
        var inconsistent = properties;
        assertThatThrownBy(() -> VnpayQueryProperties.validate(inconsistent)).isInstanceOf(IllegalStateException.class);
        var readLonger = validProperties();
        readLonger.setReadTimeout(Duration.ofSeconds(6));
        assertThatThrownBy(() -> VnpayQueryProperties.validate(readLonger)).isInstanceOf(IllegalStateException.class);
    }

    private ApplicationContextRunner enabled() {
        return runner.withPropertyValues("app.payment.provider.type=vnpay", "app.payment.vnpay.query.enabled=true")
                .withBean(ObjectMapper.class, () -> MAPPER)
                .withBean(VnpaySignature.class, () -> new VnpaySignature(SECRET))
                .withBean(VnpayProperties.class, () -> {
                    var merchant = new VnpayProperties();
                    merchant.setTmnCode("TEST1234");
                    return merchant;
                });
    }

    private VnpayQueryProperties validProperties() {
        var properties = new VnpayQueryProperties();
        properties.setServerIp("203.0.113.10");
        return properties;
    }
}
