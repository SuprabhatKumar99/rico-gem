package com.ricozknow.enterprise.sso;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
public class SamlAuthenticationFailureHandler implements AuthenticationFailureHandler {
    @Value("${ricozknow.email.frontend-base-url:http://localhost:4200}") private String frontend;
    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception) throws IOException, ServletException {
        String message = exception.getMessage() == null ? "SAML authentication failed" : exception.getMessage();
        response.sendRedirect(frontend + "/sso/callback?provider=saml&error=" + URLEncoder.encode(message, StandardCharsets.UTF_8));
    }
}
