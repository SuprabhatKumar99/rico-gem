// package com.ricozknow.enterprise.sso;

// import com.fasterxml.jackson.databind.JsonNode;
// import com.fasterxml.jackson.databind.ObjectMapper;
// import lombok.RequiredArgsConstructor;
// import org.springframework.security.saml2.core.Saml2X509Credential;
// import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistration;
// import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistrationRepository;
// import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistrations;
// import org.springframework.stereotype.Component;

// import java.io.ByteArrayInputStream;
// import java.nio.charset.StandardCharsets;
// import java.security.cert.CertificateFactory;
// import java.security.cert.X509Certificate;
// import java.util.Base64;
// import java.util.Map;
// import java.util.Iterator;
// import java.util.concurrent.ConcurrentHashMap;

// /**
//  * Tenant-aware SAML SP registry. Spring Security asks this repository for the
//  * registration identified by the tenant slug, so every SAML request is bound
//  * to one tenant's IdP configuration.
//  *
//  * A connection may either point at standard IdP metadata (metadataUrl) or use
//  * explicit metadata in settingsJson. Explicit metadata is useful for IdPs that
//  * do not publish a metadata URL and keeps the trust anchor (X.509 certificate)
//  * under tenant administration.
//  */
// @Component
// @RequiredArgsConstructor
// public class SamlRelyingPartyRegistrationRepository implements RelyingPartyRegistrationRepository {
//     private final SsoConnectionRepository repository;
//     private final ObjectMapper objectMapper;
//     private final Map<String, RelyingPartyRegistration> cache = new ConcurrentHashMap<>();

//     public Iterator<RelyingPartyRegistration> iterator() {
//         return cache.values().iterator();
//     }

//     @Override
//     public RelyingPartyRegistration findByRegistrationId(String registrationId) {
//         if (registrationId == null || registrationId.isBlank()) return null;
//         return cache.computeIfAbsent(registrationId, this::build);
//     }

//     public void evict(String tenantSlug) {
//         if (tenantSlug != null) cache.remove(tenantSlug.toLowerCase());
//     }

//     public void evictTenantId(java.util.UUID tenantId) {
//         if (tenantId == null) return;
//         repository.findByTenantIdAndProvider(tenantId, SsoConnection.Provider.SAML)
//                 .ifPresent(c -> { /* registration id is the tenant slug; cache entries are lazily refreshed */ });
//         cache.clear();
//     }

//     private RelyingPartyRegistration build(String registrationId) {
//         var connection = repository.findByTenantIdAndProvider(
//                 findTenantId(registrationId), SsoConnection.Provider.SAML)
//                 .filter(SsoConnection::isEnabled)
//                 .orElseThrow(() -> new IllegalStateException("SAML is not configured for tenant: " + registrationId));

//         try {
//             JsonNode settings = objectMapper.readTree(connection.getSettingsJson() == null ? "{}" : connection.getSettingsJson());
//             String metadataUrl = text(settings, "metadataUrl", connection.getMetadataUrl());
//             if (metadataUrl != null && !metadataUrl.isBlank()) {
//                 var builder = RelyingPartyRegistrations
//                         .fromMetadataLocation(metadataUrl)
//                         .registrationId(registrationId);
//                 String configuredSpEntityId = text(settings, "spEntityId", connection.getEntityId());
//                 if (configuredSpEntityId != null && !configuredSpEntityId.isBlank()) {
//                     builder.entityId(configuredSpEntityId);
//                 }
//                 return builder.assertionConsumerServiceLocation(acs(registrationId)).build();
//             }

//             String idpEntityId = text(settings, "idpEntityId", connection.getSamlIdpEntityId());
//             if (idpEntityId == null || idpEntityId.isBlank()) idpEntityId = text(settings, "idpEntityId", connection.getEntityId());
//             idpEntityId = requiredValue(idpEntityId, "SAML IdP entity ID");
//             String ssoUrl = text(settings, "idpSsoUrl", connection.getSamlIdpSsoUrl());
//             if (ssoUrl == null || ssoUrl.isBlank()) ssoUrl = text(settings, "idpSsoUrl", connection.getAuthorizationUrl());
//             ssoUrl = requiredValue(ssoUrl, "SAML IdP SSO URL");
//             String certificate = text(settings, "idpCertificate", connection.getSamlIdpCertificate());
//             certificate = requiredValue(certificate, "SAML IdP X.509 certificate");
//             X509Certificate x509 = parseCertificate(certificate);
//             Saml2X509Credential verificationCredential = Saml2X509Credential.verification(x509);

//             String spEntityId = text(settings, "spEntityId", connection.getSamlSpEntityId());
//             if (spEntityId == null || spEntityId.isBlank()) spEntityId = text(settings, "spEntityId", connection.getEntityId());
//             if (spEntityId == null || spEntityId.isBlank()) {
//                 spEntityId = serviceProviderEntityId(registrationId);
//             }

//             // final String entity = spEntityId;
//             // final boolean wantSigned = settings.has("wantAuthnRequestsSigned") ? settings.path("wantAuthnRequestsSigned").asBoolean(false) : connection.isSamlWantAuthnRequestsSigned();
//             // return RelyingPartyRegistration.withRegistrationId(registrationId)
//             //         .entityId(entity)
//             //         .assertionConsumerServiceLocation(acs(registrationId))
//             //         .assertingPartyMetadata(party -> party
//             //                 .entityId(idpEntityId)
//             //                 .singleSignOnServiceLocation(ssoUrl)
//             //                 .verificationX509Credentials(c -> c.add(verificationCredential))
//             //                 .wantAuthnRequestsSigned(wantSigned))
//             //         .build();

//             final String entity = spEntityId;
//             final boolean wantSigned =
//                     settings.has("wantAuthnRequestsSigned")
//                             ? settings.path("wantAuthnRequestsSigned").asBoolean(false)
//                             : connection.isSamlWantAuthnRequestsSigned();

//             return RelyingPartyRegistration
//                     .withRegistrationId(registrationId)
//                     .entityId(entity)
//                     .assertionConsumerServiceLocation(acs(registrationId))
//                     .assertingPartyDetails(party -> party
//                             .entityId(idpEntityId)
//                             .singleSignOnServiceLocation(ssoUrl)
//                             .verificationX509Credentials(c ->
//                                     c.add(verificationCredential))
//                             .wantAuthnRequestsSigned(wantSigned))
//                     .build();
//         } catch (Exception e) {
//             throw new IllegalStateException("Invalid SAML configuration for tenant " + registrationId, e);
//         }
//     }

//     private java.util.UUID findTenantId(String slug) {
//         // Repository is tenant keyed, but the SSO repository intentionally has
//         // no dependency on the tenant service. Resolve it through the SSO row.
//         return repository.findByTenantSlugAndProvider(slug, SsoConnection.Provider.SAML)
//                 .map(SsoConnection::getTenantId)
//                 .orElseThrow(() -> new IllegalArgumentException("Unknown SAML tenant: " + slug));
//     }

//     private String acs(String registrationId) {
//         return serviceProviderBaseUrl() + "/login/saml2/sso/" + registrationId;
//     }

//     private String serviceProviderEntityId(String registrationId) {
//         return serviceProviderBaseUrl() + "/saml2/metadata/" + registrationId;
//     }

//     private String serviceProviderBaseUrl() {
//         return System.getProperty("ricozknow.saml.base-url", System.getenv().getOrDefault("SAML_BASE_URL", "http://localhost:8080"));
//     }

//     private String text(JsonNode node, String field, String fallback) {
//         JsonNode value = node.get(field);
//         return value != null && !value.isNull() && !value.asText().isBlank() ? value.asText() : fallback;
//     }

//     private String requiredValue(String value, String label) {
//         if (value == null || value.isBlank()) throw new IllegalStateException(label + " is required");
//         return value;
//     }

//     private X509Certificate parseCertificate(String pem) throws Exception {
//         String normalized = pem.replace("-----BEGIN CERTIFICATE-----", "")
//                 .replace("-----END CERTIFICATE-----", "")
//                 .replaceAll("\\s", "");
//         byte[] bytes = Base64.getDecoder().decode(normalized.getBytes(StandardCharsets.US_ASCII));
//         return (X509Certificate) CertificateFactory.getInstance("X.509")
//                 .generateCertificate(new ByteArrayInputStream(bytes));
//     }
// }


package com.ricozknow.enterprise.sso;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.saml2.core.Saml2X509Credential;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistration;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistrationRepository;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistrations;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class SamlRelyingPartyRegistrationRepository
        implements RelyingPartyRegistrationRepository {

    private final SsoConnectionRepository repository;
    private final ObjectMapper objectMapper;

    private final Map<String, RelyingPartyRegistration> cache =
            new ConcurrentHashMap<>();

    // @Override
    public Iterator<RelyingPartyRegistration> iterator() {
        return cache.values().iterator();
    }

    @Override
    public RelyingPartyRegistration findByRegistrationId(String registrationId) {

        if (registrationId == null || registrationId.isBlank()) {
            return null;
        }

        String normalizedRegistrationId = registrationId.toLowerCase();

        return cache.computeIfAbsent(
                normalizedRegistrationId,
                this::build
        );
    }

    /**
     * Remove a tenant's cached SAML registration.
     *
     * Call this whenever the tenant changes its SAML configuration.
     */
    public void evict(String tenantSlug) {

        if (tenantSlug != null) {
            cache.remove(tenantSlug.toLowerCase());
        }
    }

    /**
     * Clear cached registrations associated with a tenant.
     *
     * Since registrations are lazily recreated, clearing the complete cache
     * is safe and guarantees that updated tenant SAML configuration is used.
     */
    public void evictTenantId(java.util.UUID tenantId) {

        if (tenantId == null) {
            return;
        }

        cache.clear();
    }

    private RelyingPartyRegistration build(String registrationId) {

        java.util.UUID tenantId = findTenantId(registrationId);

        SsoConnection connection =
                repository.findByTenantIdAndProvider(
                                tenantId,
                                SsoConnection.Provider.SAML
                        )
                        .filter(SsoConnection::isEnabled)
                        .orElseThrow(() ->
                                new IllegalStateException(
                                        "SAML is not configured for tenant: "
                                                + registrationId
                                )
                        );

        try {

            String settingsJson = connection.getSettingsJson();

            JsonNode settings =
                    objectMapper.readTree(
                            settingsJson == null || settingsJson.isBlank()
                                    ? "{}"
                                    : settingsJson
                    );

            /*
             * ------------------------------------------------------------
             * OPTION 1
             * IdP metadata URL
             * ------------------------------------------------------------
             */

            String metadataUrl = text(
                    settings,
                    "metadataUrl",
                    connection.getMetadataUrl()
            );

            if (metadataUrl != null && !metadataUrl.isBlank()) {

                RelyingPartyRegistration.Builder builder =
                        RelyingPartyRegistrations
                                .fromMetadataLocation(metadataUrl)
                                .registrationId(registrationId);

                String configuredSpEntityId = text(
                        settings,
                        "spEntityId",
                        connection.getEntityId()
                );

                if (configuredSpEntityId != null
                        && !configuredSpEntityId.isBlank()) {

                    builder.entityId(configuredSpEntityId);
                }

                return builder
                        .assertionConsumerServiceLocation(
                                acs(registrationId)
                        )
                        .build();
            }

            /*
             * ------------------------------------------------------------
             * OPTION 2
             * Explicit IdP configuration
             * ------------------------------------------------------------
             */

            String idpEntityId = text(
                    settings,
                    "idpEntityId",
                    connection.getSamlIdpEntityId()
            );

            if (idpEntityId == null || idpEntityId.isBlank()) {

                idpEntityId = text(
                        settings,
                        "idpEntityId",
                        connection.getEntityId()
                );
            }

            idpEntityId = requiredValue(
                    idpEntityId,
                    "SAML IdP entity ID"
            );

            String ssoUrl = text(
                    settings,
                    "idpSsoUrl",
                    connection.getSamlIdpSsoUrl()
            );

            if (ssoUrl == null || ssoUrl.isBlank()) {

                ssoUrl = text(
                        settings,
                        "idpSsoUrl",
                        connection.getAuthorizationUrl()
                );
            }

            ssoUrl = requiredValue(
                    ssoUrl,
                    "SAML IdP SSO URL"
            );

            String certificate = text(
                    settings,
                    "idpCertificate",
                    connection.getSamlIdpCertificate()
            );

            certificate = requiredValue(
                    certificate,
                    "SAML IdP X.509 certificate"
            );

            X509Certificate x509Certificate =
                    parseCertificate(certificate);

            Saml2X509Credential verificationCredential =
                    Saml2X509Credential.verification(
                            x509Certificate
                    );

            /*
             * ------------------------------------------------------------
             * SP ENTITY ID
             * ------------------------------------------------------------
             */

            String spEntityId = text(
                    settings,
                    "spEntityId",
                    connection.getSamlSpEntityId()
            );

            if (spEntityId == null || spEntityId.isBlank()) {

                spEntityId = text(
                        settings,
                        "spEntityId",
                        connection.getEntityId()
                );
            }

            if (spEntityId == null || spEntityId.isBlank()) {

                spEntityId =
                        serviceProviderEntityId(registrationId);
            }

            /*
             * ------------------------------------------------------------
             * AUTHN REQUEST SIGNING
             * ------------------------------------------------------------
             */

            boolean wantSigned;

            if (settings.has("wantAuthnRequestsSigned")) {

                wantSigned =
                        settings
                                .path("wantAuthnRequestsSigned")
                                .asBoolean(false);

            } else {

                wantSigned =
                        connection.isSamlWantAuthnRequestsSigned();
            }

            /*
             * ------------------------------------------------------------
             * BUILD REGISTRATION
             * ------------------------------------------------------------
             *
             * Spring Security 6.x uses assertingPartyDetails(...)
             * for explicit IdP configuration.
             */

            final String finalIdpEntityId = idpEntityId;
            final String finalSsoUrl = ssoUrl;
            final String finalSpEntityId = spEntityId;

            return RelyingPartyRegistration
                    .withRegistrationId(registrationId)

                    .entityId(finalSpEntityId)

                    .assertionConsumerServiceLocation(
                            acs(registrationId)
                    )

                    .assertingPartyDetails(party -> party

                            .entityId(finalIdpEntityId)

                            .singleSignOnServiceLocation(
                                    finalSsoUrl
                            )

                            .verificationX509Credentials(
                                    credentials ->
                                            credentials.add(
                                                    verificationCredential
                                            )
                            )

                            .wantAuthnRequestsSigned(
                                    wantSigned
                            )
                    )

                    .build();

        } catch (Exception e) {

            throw new IllegalStateException(
                    "Invalid SAML configuration for tenant "
                            + registrationId,
                    e
            );
        }
    }

    /**
     * Resolve tenant ID using the tenant slug stored in the SSO connection.
     */
    private java.util.UUID findTenantId(String slug) {

        return repository
                .findByTenantSlugAndProvider(
                        slug,
                        SsoConnection.Provider.SAML
                )
                .map(SsoConnection::getTenantId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Unknown SAML tenant: " + slug
                        )
                );
    }

    /**
     * SAML Assertion Consumer Service URL.
     */
    private String acs(String registrationId) {

        return serviceProviderBaseUrl()
                + "/login/saml2/sso/"
                + registrationId;
    }

    /**
     * SP Entity ID.
     */
    private String serviceProviderEntityId(
            String registrationId
    ) {

        return serviceProviderBaseUrl()
                + "/saml2/metadata/"
                + registrationId;
    }

    /**
     * External backend base URL.
     *
     * Production:
     *
     * SAML_BASE_URL=https://api.ricozknow.com
     *
     * Local:
     *
     * SAML_BASE_URL=http://localhost:8080
     */
    private String serviceProviderBaseUrl() {

        String configured =
                System.getProperty(
                        "ricozknow.saml.base-url"
                );

        if (configured != null
                && !configured.isBlank()) {

            return removeTrailingSlash(configured);
        }

        configured =
                System.getenv("SAML_BASE_URL");

        if (configured != null
                && !configured.isBlank()) {

            return removeTrailingSlash(configured);
        }

        return "http://localhost:8080";
    }

    private String removeTrailingSlash(String value) {

        if (value == null) {
            return null;
        }

        return value.endsWith("/")
                ? value.substring(0, value.length() - 1)
                : value;
    }

    /**
     * Read a value from settings JSON and fall back to the database column.
     */
    private String text(
            JsonNode node,
            String field,
            String fallback
    ) {

        JsonNode value = node.get(field);

        if (value != null
                && !value.isNull()
                && !value.asText().isBlank()) {

            return value.asText();
        }

        return fallback;
    }

    private String requiredValue(
            String value,
            String label
    ) {

        if (value == null || value.isBlank()) {

            throw new IllegalStateException(
                    label + " is required"
            );
        }

        return value;
    }

    /**
     * Parse PEM or Base64 encoded X.509 certificate.
     */
    private X509Certificate parseCertificate(
            String pem
    ) throws Exception {

        String normalized =
                pem
                        .replace(
                                "-----BEGIN CERTIFICATE-----",
                                ""
                        )
                        .replace(
                                "-----END CERTIFICATE-----",
                                ""
                        )
                        .replaceAll(
                                "\\s+",
                                ""
                        );

        byte[] bytes =
                Base64.getDecoder().decode(
                        normalized.getBytes(
                                StandardCharsets.US_ASCII
                        )
                );

        return (X509Certificate)
                CertificateFactory
                        .getInstance("X.509")
                        .generateCertificate(
                                new ByteArrayInputStream(bytes)
                        );
    }
}