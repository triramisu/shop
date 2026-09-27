package com.shop.identity.internal.configuration;

import com.shop.identity.internal.constant.IdentityApiPaths;
import com.shop.identity.internal.security.AuthenticationRateLimitInterceptor;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
class AuthenticationRateLimitWebConfiguration implements WebMvcConfigurer {

    AuthenticationRateLimitInterceptor authenticationRateLimitInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authenticationRateLimitInterceptor).addPathPatterns(IdentityApiPaths.AUTH_PATTERN);
    }
}
