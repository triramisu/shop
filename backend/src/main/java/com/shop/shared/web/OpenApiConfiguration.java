package com.shop.shared.web;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.modulith.NamedInterface;
import org.springframework.util.StringUtils;

@Configuration
@NamedInterface("web")
@RequiredArgsConstructor
@EnableConfigurationProperties(OpenApiProperties.class)
public class OpenApiConfiguration {

    public static final String BEARER_AUTH_SCHEME = "bearerAuth";

    private final OpenApiProperties properties;

    @Bean
    OpenAPI openApi() {
        return new OpenAPI()
                .components(new Components().addSecuritySchemes(BEARER_AUTH_SCHEME, bearerSecurityScheme()))
                .info(apiInfo())
                .servers(List.of(apiServer()));
    }

    private SecurityScheme bearerSecurityScheme() {
        return new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .in(SecurityScheme.In.HEADER);
    }

    private Info apiInfo() {
        OpenApiProperties.ApiInfo info = properties.info();
        OpenApiProperties.Contact contact = info.contact();

        return new Info()
                .title(info.title())
                .version(info.version())
                .description(info.description())
                .contact(apiContact(contact));
    }

    private Contact apiContact(OpenApiProperties.Contact properties) {
        Contact contact = new Contact().name(properties.name());
        if (StringUtils.hasText(properties.url())) {
            contact.url(properties.url());
        }
        if (StringUtils.hasText(properties.email())) {
            contact.email(properties.email());
        }
        return contact;
    }

    private Server apiServer() {
        return new Server()
                .url(properties.server().url())
                .description(properties.server().description());
    }
}
