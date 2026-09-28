package com.shop.identity.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

public final class IdentityApiTestClient {

    public static final String DEFAULT_PASSWORD = "Str0ngPassword!";

    private final MockMvc mockMvc;

    public IdentityApiTestClient(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    public UUID register(String username) throws Exception {
        return register(username, username + "@example.com", DEFAULT_PASSWORD);
    }

    public UUID register(String username, String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationJson(username, email, password)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(1000))
                .andReturn();
        return UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.result.id"));
    }

    public TokenPair authenticate(String username) throws Exception {
        return authenticate(username, DEFAULT_PASSWORD);
    }

    public TokenPair authenticate(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(authenticationJson(username, password)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1000))
                .andExpect(jsonPath("$.result.authenticated").value(true))
                .andExpect(jsonPath("$.result.tokenType").value("Bearer"))
                .andReturn();
        String body = result.getResponse().getContentAsString();
        return new TokenPair(JsonPath.read(body, "$.result.accessToken"), JsonPath.read(body, "$.result.refreshToken"));
    }

    public static String bearer(String token) {
        return "Bearer " + token;
    }

    public static String tokenJson(String token) {
        return """
                {"token":"%s"}
                """.formatted(token);
    }

    private String registrationJson(String username, String email, String password) {
        return """
                {
                  "username":"%s",
                  "email":"%s",
                  "password":"%s"
                }
                """.formatted(username, email, password);
    }

    private String authenticationJson(String username, String password) {
        return """
                {"username":"%s","password":"%s"}
                """.formatted(username, password);
    }

    public record TokenPair(String accessToken, String refreshToken) {}
}
