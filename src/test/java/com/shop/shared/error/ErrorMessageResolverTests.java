package com.shop.shared.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;

class ErrorMessageResolverTests {

    @Test
    void resolvesEveryErrorCodeFromTheVietnameseMessageBundle() throws Exception {
        Properties messages = new Properties();
        try (var input = getClass().getResourceAsStream("/message.properties")) {
            assertThat(input).isNotNull();
            messages.load(new InputStreamReader(input, StandardCharsets.UTF_8));
        }

        Set<String> errorKeys =
                Arrays.stream(ErrorCode.values()).map(ErrorCode::getMessageKey).collect(Collectors.toSet());
        assertThat(messages.stringPropertyNames()).containsAll(errorKeys);
        assertThat(errorKeys)
                .allSatisfy(key -> assertThat(messages.getProperty(key)).isNotBlank());

        ErrorMessageResolver resolver = new ErrorMessageResolver(createMessageSource());
        assertThat(resolver.resolve(ErrorCode.RESOURCE_NOT_FOUND)).isEqualTo("Không tìm thấy tài nguyên");
        assertThat(resolver.resolve(ErrorCode.UNAUTHORIZED)).isEqualTo("Bạn không có quyền thực hiện thao tác này");
    }

    private ResourceBundleMessageSource createMessageSource() {
        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
        messageSource.setBasename("message");
        messageSource.setDefaultEncoding(StandardCharsets.UTF_8.name());
        return messageSource;
    }
}
