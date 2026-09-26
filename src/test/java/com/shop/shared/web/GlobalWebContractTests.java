package com.shop.shared.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shop.shared.error.AppException;
import com.shop.shared.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

class GlobalWebContractTests {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new StubController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new CorrelationIdFilter())
                .build();
    }

    @Test
    void returnsApiResponseAndCorrelationId() throws Exception {
        mockMvc.perform(get("/test/missing").header(CorrelationIdFilter.HEADER_NAME, "contract-test-123"))
                .andExpect(status().isNotFound())
                .andExpect(header().string(CorrelationIdFilter.HEADER_NAME, "contract-test-123"))
                .andExpect(jsonPath("$.code").value(1011))
                .andExpect(jsonPath("$.message").value("Resource not found"));
    }

    @RestController
    @RequestMapping("/test")
    static class StubController {

        @GetMapping("/missing")
        void missing() {
            throw new AppException(ErrorCode.RESOURCE_NOT_FOUND);
        }
    }
}
