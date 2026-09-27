package com.shop.identity;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shop.identity.internal.captcha.service.AdaptiveCaptchaService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest(
        properties = {
            "app.security.rate-limit.enabled=true",
            "app.security.rate-limit.login.capacity=2",
            "app.security.rate-limit.login.refill-period=1m",
            "app.security.rate-limit.captcha.capacity=2",
            "app.security.rate-limit.captcha.refill-period=1m",
            "app.security.rate-limit.max-buckets=100",
            "app.security.captcha.enabled=true"
        })
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthenticationRateLimitIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AdaptiveCaptchaService adaptiveCaptchaService;

    @AfterEach
    void cleanCaptchaState() {
        jdbcTemplate.update("DELETE FROM xac_thuc_thu_thach_captcha");
        jdbcTemplate.update("DELETE FROM xac_thuc_dang_nhap_that_bai");
    }

    @Test
    void limitsLoginByRemoteAddressAndReturnsTheApiErrorContract() throws Exception {
        loginFrom("192.0.2.10", "198.51.100.1").andExpect(status().isUnauthorized());
        loginFrom("192.0.2.10", "198.51.100.2").andExpect(status().isUnauthorized());

        loginFrom("192.0.2.10", "203.0.113.99")
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
                .andExpect(jsonPath("$.code").value(1024))
                .andExpect(jsonPath("$.message").value("Too many requests. Please try again later"));

        loginFrom("192.0.2.11", null).andExpect(status().isUnauthorized());
    }

    @Test
    void doesNotRateLimitUnrelatedEndpoints() throws Exception {
        for (int request = 0; request < 3; request++) {
            mockMvc.perform(get("/actuator/health").with(remoteAddress("192.0.2.20")))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void limitsCaptchaGenerationByRemoteAddress() throws Exception {
        adaptiveCaptchaService.recordFailure("rate-limit-captcha");
        adaptiveCaptchaService.recordFailure("rate-limit-captcha");
        adaptiveCaptchaService.recordFailure("rate-limit-captcha");

        captchaFrom("192.0.2.30").andExpect(status().isOk());
        captchaFrom("192.0.2.30").andExpect(status().isOk());
        captchaFrom("192.0.2.30")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value(1024));
    }

    private org.springframework.test.web.servlet.ResultActions loginFrom(String remoteAddress, String forwardedFor)
            throws Exception {
        var request = post("/api/auth/token")
                .with(remoteAddress(remoteAddress))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "username": "missing-user",
                          "password": "wrong-password"
                        }
                        """);
        if (forwardedFor != null) {
            request.header("X-Forwarded-For", forwardedFor);
        }
        return mockMvc.perform(request);
    }

    private RequestPostProcessor remoteAddress(String remoteAddress) {
        return request -> {
            request.setRemoteAddr(remoteAddress);
            return request;
        };
    }

    private org.springframework.test.web.servlet.ResultActions captchaFrom(String remoteAddress) throws Exception {
        return mockMvc.perform(post("/api/auth/captcha")
                .with(remoteAddress(remoteAddress))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"username":"rate-limit-captcha"}
                        """));
    }
}
