package com.shop;

import static org.assertj.core.api.Assertions.assertThat;

import com.shop.shared.web.OpenApiConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.NamedInterface;
import org.springframework.modulith.core.ApplicationModules;

class ModularArchitectureTests {

    @Test
    void verifiesModuleBoundaries() {
        ApplicationModules.of(ShopApplication.class).verify();
    }

    @Test
    void exposesOpenApiConfigurationThroughSharedWebContract() {
        NamedInterface namedInterface = OpenApiConfiguration.class.getAnnotation(NamedInterface.class);

        assertThat(namedInterface).isNotNull();
        assertThat(namedInterface.value()).contains("web");
    }
}
