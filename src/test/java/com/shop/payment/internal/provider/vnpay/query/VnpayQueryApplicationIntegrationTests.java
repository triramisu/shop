package com.shop.payment.internal.provider.vnpay.query;

import static org.assertj.core.api.Assertions.assertThat;

import com.shop.payment.internal.provider.vnpay.VnpayPaymentProvider;
import com.shop.payment.provider.PaymentProviderOperations;
import com.shop.payment.provider.PaymentStatusQueryOperations;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(
        properties = {
            "app.payment.provider.type=vnpay",
            "app.payment.vnpay.tmn-code=TEST1234",
            "app.payment.vnpay.hash-secret=sandbox-query-test-key-01234567890123456789",
            "app.payment.vnpay.public-base-url=https://api.example.com",
            "app.payment.vnpay.allowed-hosts=api.example.com",
            "app.payment.vnpay.query.enabled=true",
            "app.payment.vnpay.query.server-ip=203.0.113.10"
        })
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class VnpayQueryApplicationIntegrationTests {
    @Autowired
    ApplicationContext context;

    @Test
    void startsRealApplicationWithQueryOptInWithoutReplacingCheckoutOrCallingMerchant() {
        assertThat(context.getBeansOfType(PaymentStatusQueryOperations.class)).hasSize(1);
        assertThat(context.getBean(PaymentStatusQueryOperations.class).providerCode())
                .isEqualTo("VNPAY");
        assertThat(context.getBean(PaymentProviderOperations.class)).isInstanceOf(VnpayPaymentProvider.class);
    }
}
