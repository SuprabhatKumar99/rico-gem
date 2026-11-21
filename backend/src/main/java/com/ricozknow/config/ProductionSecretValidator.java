package com.ricozknow.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/**
 * Fails fast in the production profile when placeholder/default credentials are
 * still configured. Development/test profiles intentionally remain permissive.
 */
@Component
@Profile("prod")
public class ProductionSecretValidator {
    @Value("${JWT_SECRET:}") private String jwtSecret;
    @Value("${DB_HOST:}") private String dbHost;
    @Value("${DB_NAME:}") private String dbName;
    @Value("${DB_USER:${DB_USERNAME:}}") private String dbUser;
    @Value("${DB_PASSWORD:}") private String dbPassword;
    @Value("${S3_ACCESS_KEY:}") private String s3AccessKey;
    @Value("${S3_SECRET_KEY:}") private String s3SecretKey;
    @Value("${MAIL_PASSWORD:}") private String mailPassword;
    @Value("${MAIL_HOST:}") private String mailHost;
    @Value("${AI_EMBEDDING_API_KEY:${RAG_EMBEDDING_API_KEY:}}") private String embeddingKey;
    @Value("${AI_GENERATION_API_KEY:${RAG_GENERATION_API_KEY:}}") private String generationKey;
    @Value("${AI_EMBEDDING_PROVIDER:${RAG_EMBEDDING_PROVIDER:openai-compatible}}") private String embeddingProvider;
    @Value("${AI_GENERATION_PROVIDER:${RAG_GENERATION_PROVIDER:openai-compatible}}") private String generationProvider;
    @Value("${RAG_ENABLED:false}") private boolean ragEnabled;
    @Value("${S3_BUCKET:}") private String bucket;
    @Value("${FRONTEND_BASE_URL:}") private String frontendBaseUrl;

    @PostConstruct
    void validate() {
        require(jwtSecret, "JWT_SECRET", 32);
        requireNonBlank(dbHost, "DB_HOST");
        requireNonBlank(dbName, "DB_NAME");
        requireNonBlank(dbUser, "DB_USER");
        requireNonBlank(dbPassword, "DB_PASSWORD");
        requireNonBlank(s3AccessKey, "S3_ACCESS_KEY");
        requireNonBlank(s3SecretKey, "S3_SECRET_KEY");
        requireNonBlank(bucket, "S3_BUCKET");
        requireNonBlank(frontendBaseUrl, "FRONTEND_BASE_URL");
        requireNonBlank(mailHost, "MAIL_HOST");
        requireNonBlank(mailPassword, "MAIL_PASSWORD");
        if (ragEnabled) {
            if (!"ollama".equalsIgnoreCase(embeddingProvider)) requireNonBlank(embeddingKey, "AI_EMBEDDING_API_KEY");
            if (!"ollama".equalsIgnoreCase(generationProvider)) requireNonBlank(generationKey, "AI_GENERATION_API_KEY");
        }
        rejectPlaceholder(dbPassword, "DB_PASSWORD");
        rejectPlaceholder(s3AccessKey, "S3_ACCESS_KEY");
        rejectPlaceholder(s3SecretKey, "S3_SECRET_KEY");
        rejectPlaceholder(jwtSecret, "JWT_SECRET");
    }

    private void require(String value, String name, int minLength) {
        if (value == null || value.isBlank() || value.length() < minLength) {
            throw new IllegalStateException(name + " must be set and at least " + minLength + " characters in production");
        }
    }

    private void requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " must be set in production");
        }
    }

    private void rejectPlaceholder(String value, String name) {
        String v = value == null ? "" : value.toLowerCase();
        if (v.contains("change_me") || v.contains("replace-with") || v.equals("minioadmin") || v.equals("ricozknow")) {
            throw new IllegalStateException(name + " contains a placeholder/default value");
        }
    }
}
