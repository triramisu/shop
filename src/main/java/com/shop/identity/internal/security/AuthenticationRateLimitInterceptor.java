package com.shop.identity.internal.security;

import com.shop.identity.internal.constant.IdentityApiPaths;
import com.shop.shared.error.ErrorCode;
import com.shop.shared.error.ErrorMessageResolver;
import com.shop.shared.web.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AuthenticationRateLimitInterceptor implements HandlerInterceptor {

    private static final Set<String> PROTECTED_PATHS = Set.of(
            IdentityApiPaths.REGISTER,
            IdentityApiPaths.TOKEN,
            IdentityApiPaths.CAPTCHA,
            IdentityApiPaths.INTROSPECT,
            IdentityApiPaths.REFRESH,
            IdentityApiPaths.LOGOUT);

    AuthenticationRateLimitService rateLimitService;
    ObjectMapper objectMapper;
    ErrorMessageResolver messageResolver;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        String path = resolvePath(request);
        if (!HttpMethod.POST.matches(request.getMethod()) || !PROTECTED_PATHS.contains(path)) {
            return true;
        }

        AuthenticationRateLimitService.RateLimitDecision decision =
                rateLimitService.tryConsume(path, resolveClientAddress(request));
        if (decision.allowed()) {
            return true;
        }

        ErrorCode errorCode = ErrorCode.RATE_LIMIT_EXCEEDED;
        ApiResponse<Void> body = ApiResponse.<Void>builder()
                .code(errorCode.getCode())
                .message(messageResolver.resolve(errorCode, request.getLocale()))
                .build();

        response.setStatus(errorCode.getStatusCode().value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(decision.retryAfterSeconds()));
        objectMapper.writeValue(response.getOutputStream(), body);
        return false;
    }

    private String resolveClientAddress(HttpServletRequest request) {
        String remoteAddress = request.getRemoteAddr();
        return remoteAddress == null || remoteAddress.isBlank() ? "unknown" : remoteAddress;
    }

    private String resolvePath(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        String contextPath = request.getContextPath();
        return contextPath.isEmpty() ? requestUri : requestUri.substring(contextPath.length());
    }
}
