package com.shop.identity.internal.controller;

import com.nimbusds.jose.JOSEException;
import com.shop.identity.internal.captcha.dto.request.CaptchaRequest;
import com.shop.identity.internal.captcha.dto.response.CaptchaResponse;
import com.shop.identity.internal.captcha.service.AdaptiveCaptchaService;
import com.shop.identity.internal.constant.IdentityApiPaths;
import com.shop.identity.internal.dto.request.AuthenticationRequest;
import com.shop.identity.internal.dto.request.IntrospectRequest;
import com.shop.identity.internal.dto.request.LogoutRequest;
import com.shop.identity.internal.dto.request.RefreshRequest;
import com.shop.identity.internal.dto.request.RegisterUserRequest;
import com.shop.identity.internal.dto.response.AuthenticationResponse;
import com.shop.identity.internal.dto.response.IntrospectResponse;
import com.shop.identity.internal.dto.response.UserResponse;
import com.shop.identity.internal.service.AuthenticationService;
import com.shop.identity.internal.service.RegistrationService;
import com.shop.shared.web.ApiResponse;
import jakarta.validation.Valid;
import java.text.ParseException;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(IdentityApiPaths.AUTH_BASE)
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AuthenticationController {

    AuthenticationService authenticationService;
    RegistrationService registrationService;
    AdaptiveCaptchaService adaptiveCaptchaService;

    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping(IdentityApiPaths.REGISTER_SEGMENT)
    ApiResponse<UserResponse> register(@Valid @RequestBody RegisterUserRequest request) {
        var result = registrationService.register(request);
        return ApiResponse.<UserResponse>builder().result(result).build();
    }

    @PostMapping(IdentityApiPaths.TOKEN_SEGMENT)
    ApiResponse<AuthenticationResponse> authenticate(@Valid @RequestBody AuthenticationRequest request) {
        AuthenticationResponse result = authenticationService.authenticate(request);
        return ApiResponse.<AuthenticationResponse>builder().result(result).build();
    }

    @PostMapping(IdentityApiPaths.CAPTCHA_SEGMENT)
    ApiResponse<CaptchaResponse> captcha(@Valid @RequestBody CaptchaRequest request) {
        var result = adaptiveCaptchaService.issueChallenge(request.getUsername());
        return ApiResponse.<CaptchaResponse>builder().result(result).build();
    }

    @PostMapping(IdentityApiPaths.INTROSPECT_SEGMENT)
    ApiResponse<IntrospectResponse> introspect(@Valid @RequestBody IntrospectRequest request) {
        IntrospectResponse result = authenticationService.introspect(request);
        return ApiResponse.<IntrospectResponse>builder().result(result).build();
    }

    @PostMapping(IdentityApiPaths.REFRESH_SEGMENT)
    ApiResponse<AuthenticationResponse> refresh(@Valid @RequestBody RefreshRequest request)
            throws ParseException, JOSEException {
        AuthenticationResponse result = authenticationService.refreshToken(request);
        return ApiResponse.<AuthenticationResponse>builder().result(result).build();
    }

    @PostMapping(IdentityApiPaths.LOGOUT_SEGMENT)
    ApiResponse<Void> logout(@Valid @RequestBody LogoutRequest request) throws ParseException, JOSEException {
        authenticationService.logout(request);
        return ApiResponse.<Void>builder().build();
    }
}
