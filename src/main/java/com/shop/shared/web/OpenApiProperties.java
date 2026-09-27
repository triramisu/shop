package com.shop.shared.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.open-api")
public record OpenApiProperties(
        @Valid @NotNull ApiInfo info, @Valid @NotNull ApiServer server) {

    public record ApiInfo(
            @NotBlank String title,
            @NotBlank String version,
            @NotBlank String description,
            @Valid @NotNull Contact contact) {}

    public record Contact(@NotBlank String name, String url, String email) {}

    public record ApiServer(@NotBlank String url, @NotBlank String description) {}
}
