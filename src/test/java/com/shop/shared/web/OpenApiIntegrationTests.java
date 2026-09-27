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

@SpringBootTest(
        properties = {
            "app.open-api.info.title=Shop Integration API",
            "app.open-api.info.contact.name=Integration Team",
            "app.open-api.server.url=/integration",
            "app.open-api.server.description=Integration environment"
        })
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
                .andExpect(jsonPath("$.info.title").value("Shop Integration API"))
                .andExpect(jsonPath("$.info.version").value("v1"))
                .andExpect(jsonPath("$.info.description").value("REST API for the Shop modular monolith"))
                .andExpect(jsonPath("$.info.contact.name").value("Integration Team"))
                .andExpect(jsonPath("$.servers[0].url").value("/integration"))
                .andExpect(jsonPath("$.servers[0].description").value("Integration environment"))
                .andExpect(
                        jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme")
                        .value("bearer"))
                .andExpect(jsonPath("$.paths['/api/auth/captcha'].post").exists())
                .andExpect(jsonPath("$.paths['/api/auth/token'].post").exists())
                .andExpect(jsonPath("$.paths['/api/auth/my-info'].get").exists())
                .andExpect(jsonPath("$.paths['/api/auth/my-info'].put").exists())
                .andExpect(jsonPath("$.paths['/api/auth/my-info/password'].put").exists())
                .andExpect(jsonPath("$.paths['/api/system-administration/users'].get")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/system-administration/users/{userId}/status'].patch")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/system-administration/users/{userId}/roles'].put")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/system-administration/roles'].post")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/system-administration/permissions'].get")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/auth/token'].post.security").doesNotExist())
                .andExpect(
                        jsonPath("$.paths['/api/auth/register'].post.security").doesNotExist())
                .andExpect(
                        jsonPath("$.paths['/api/auth/captcha'].post.security").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/auth/my-info'].get.security[0].bearerAuth")
                        .isArray())
                .andExpect(jsonPath("$.paths['/api/system-administration/users'].get.security[0].bearerAuth")
                        .isArray());
    }

    @Test
    void exposesSwaggerUiWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/swagger-ui.html")).andExpect(status().is3xxRedirection());
        mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    }
}
