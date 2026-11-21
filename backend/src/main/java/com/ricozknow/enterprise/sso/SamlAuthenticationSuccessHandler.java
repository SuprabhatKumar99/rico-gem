package com.ricozknow.enterprise.sso;

import com.ricozknow.auth.AuthService;
import com.ricozknow.auth.dto.LoginResult;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.saml2.provider.service.authentication.Saml2AuthenticatedPrincipal;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
@RequiredArgsConstructor
public class SamlAuthenticationSuccessHandler implements AuthenticationSuccessHandler {
    private final AuthService authService;
    private final SsoLoginHandoffService handoffs;
    @Value("${ricozknow.email.frontend-base-url:http://localhost:4200}") private String frontend;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {
        Saml2AuthenticatedPrincipal principal = (Saml2AuthenticatedPrincipal) authentication.getPrincipal();
        String registrationId = principal.getRelyingPartyRegistrationId();
        String email = first(principal, "email", "mail", "http://schemas.xmlsoap.org/ws/2005/05/identity/claims/emailaddress", "uid");
        String name = first(principal, "name", "displayName", "http://schemas.xmlsoap.org/ws/2005/05/identity/claims/name");
        if (email == null || email.isBlank()) throw new ServletException("SAML assertion did not contain an email attribute");
        if (name == null || name.isBlank()) name = email;
        LoginResult result = authService.ssoLogin(registrationId, email, name, request.getRemoteAddr());
        String code = handoffs.create(result);
        response.sendRedirect(frontend + "/sso/callback?provider=saml&code=" + URLEncoder.encode(code, StandardCharsets.UTF_8));
    }

    private String first(Saml2AuthenticatedPrincipal p, String... names) {
        for (String n : names) {
            List<Object> values = p.getAttribute(n);
            if (values != null && !values.isEmpty() && values.get(0) != null) return String.valueOf(values.get(0));
        }
        return null;
    }
}
