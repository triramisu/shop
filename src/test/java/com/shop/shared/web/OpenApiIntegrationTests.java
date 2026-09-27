package com.shop.shared.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OpenApiIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void exposesVersionedApiDocumentationWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").isNotEmpty())
                .andExpect(jsonPath("$.info.title").value("Shop API"))
                .andExpect(jsonPath("$.info.version").value("v1"))
                .andExpect(
                        jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme")
                        .value("bearer"))
                .andExpect(jsonPath("$.paths['/api/auth/captcha'].post").exists())
                .andExpect(jsonPath("$.paths['/api/auth/token'].post").exists())
                .andExpect(jsonPath("$.paths['/api/auth/my-info'].get").exists())
                .andExpect(jsonPath("$.paths['/api/auth/my-info'].put").exists())
                .andExpect(jsonPath("$.paths['/api/auth/my-info/password'].put").exists())
                .andExpect(jsonPath("$.paths['/api/auth/my-info'].get.security[0].bearerAuth")
                        .isArray());
    }

    @Test
    void exposesSwaggerUiWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/swagger-ui.html")).andExpect(status().is3xxRedirection());
        mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    }
}
