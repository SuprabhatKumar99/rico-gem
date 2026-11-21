package com.ricozknow.rag;

import org.springframework.http.HttpStatus;

public class RagApiException extends RuntimeException {
    private final String code;
    private final HttpStatus status;
    private final boolean retryable;

    public RagApiException(String code, String message, HttpStatus status, boolean retryable) {
        super(message);
        this.code = code;
        this.status = status;
        this.retryable = retryable;
    }
    public String getCode() { return code; }
    public HttpStatus getStatus() { return status; }
    public boolean isRetryable() { return retryable; }
}
