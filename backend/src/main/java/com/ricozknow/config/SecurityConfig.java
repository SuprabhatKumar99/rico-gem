package com.ricozknow.config;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;


import com.ricozknow.auth.JwtAuthFilter;
import com.ricozknow.rag.RagApiKeyAuthFilter;
import com.ricozknow.tenant.TenantResolverFilter;
import com.ricozknow.enterprise.metering.UsageMeteringFilter;
import com.ricozknow.enterprise.sso.SamlAuthenticationFailureHandler;
import com.ricozknow.enterprise.sso.SamlAuthenticationSuccessHandler;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
/**
 * The frontend is never trusted as the authorization boundary (spec section 3);
 * every rule below is enforced server-side regardless of what Angular route
 * guards already checked. Coarse-grained rules live in the filter chain below;
 * finer-grained per-action rules (e.g. "categories are publicly readable but
 * only EDITOR/ADMIN can write") use @PreAuthorize on individual controller
 * methods, enabled here.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final RagApiKeyAuthFilter ragApiKeyAuthFilter;
    private final TenantResolverFilter tenantResolverFilter;
    private final UsageMeteringFilter usageMeteringFilter;
    private final RelyingPartyRegistrationRepository samlRelyingPartyRegistrationRepository;
    private final SamlAuthenticationSuccessHandler samlAuthenticationSuccessHandler;
    private final SamlAuthenticationFailureHandler samlAuthenticationFailureHandler;

    @Value("${ALLOWED_ORIGINS:http://localhost:4200}")
    private String allowedOrigins;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                // The bearer-token REST APIs do not use browser sessions for auth.
                // CookieOriginGuardFilter independently enforces trusted browser
                // origins on refresh/logout (SameSite may be Lax/None for deployment).
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/auth/login", "/api/v1/auth/refresh", "/api/v1/auth/register", "/api/v1/auth/logout",
                                "/api/v1/auth/verify-email", "/api/v1/auth/resend-verification",
                                "/api/v1/auth/forgot-password", "/api/v1/auth/reset-password", "/api/v1/auth/accept-invitation",
                                "/api/v1/auth/social/*/start", "/api/v1/auth/social/*/callback", "/api/v1/auth/social/finish", "/api/v1/tenants/lookup", "/api/v1/customer/auth/**", "/api/v1/widget/**", "/widget/ricozknow.js", "/api/v1/auth/sso/**", "/api/v1/billing/webhooks/stripe", "/scim/v2/**",
                                "/saml2/**", "/login/saml2/**", "/api/v1/connectors/oauth/callback/**", "/api/v1/connectors/webhooks/**").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info", "/actuator/prometheus").permitAll()
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/api/v1/portal/**").permitAll() // anonymous customer portal reads/feedback
                        .requestMatchers("/api/v1/search/**", "/api/v1/categories/**").permitAll() // public customer portal reads; visibility enforced in service layer
                        .requestMatchers("/api/v1/internal/collaboration/**").permitAll() // shared-key protected; no user bearer token required
                        .requestMatchers("/api/v1/billing/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/agent/**").hasAnyRole("ADMIN", "AGENT_VIEWER")
                        .requestMatchers("/mcp", "/mcp/**").hasAuthority("SCOPE_MCP_CONNECT")
                        .requestMatchers("/api/v1/rag/**").hasAuthority("SCOPE_RAG_SEARCH")
                        .requestMatchers("/api/v1/articles/*/review").hasAnyRole("ADMIN", "REVIEWER")
                        .requestMatchers("/api/v1/articles/**").hasAnyRole("ADMIN", "EDITOR", "REVIEWER")
                        .anyRequest().authenticated())
                // Order matters: JWT auth first (populates SecurityContext), then tenant
                // resolution (which trusts an authenticated principal's tenantId over hostname).
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(ragApiKeyAuthFilter, JwtAuthFilter.class)
                .addFilterAfter(tenantResolverFilter, RagApiKeyAuthFilter.class)
                .addFilterAfter(usageMeteringFilter, TenantResolverFilter.class)
                .saml2Login(saml -> saml
                        .relyingPartyRegistrationRepository(samlRelyingPartyRegistrationRepository)
                        .successHandler(samlAuthenticationSuccessHandler)
                        .failureHandler(samlAuthenticationFailureHandler))
                .saml2Metadata(Customizer.withDefaults())
                .exceptionHandling(exceptions -> exceptions
                    .authenticationEntryPoint((request, response, authException) -> {
                        response.sendError(
                                HttpServletResponse.SC_UNAUTHORIZED,
                                "Authentication required"
                        );
                    })
                
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    response.sendError(
                            HttpServletResponse.SC_FORBIDDEN,
                            "Access denied"
                    );
                })
        );


        return http.build();
    }


    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        
        configuration.setAllowedOriginPatterns(
            Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isBlank())
                .toList()
        );

        configuration.setAllowedMethods(List.of(
            "GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"
        ));

        configuration.setAllowedHeaders(List.of(
            "Authorization",
            "Content-Type",
            "Accept",
            "Origin",
            "X-Requested-With",
            "X-API-Key"
        ));

        configuration.setExposedHeaders(List.of("Authorization"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source =
            new UrlBasedCorsConfigurationSource();

        source.registerCorsConfiguration("/**", configuration);

        return source;
    }
}