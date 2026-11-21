package com.ricozknow.ai;

import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

final class ProviderHttpSupport {
    private ProviderHttpSupport() {}

    static RestClient.RequestBodySpec post(RestClient client, String endpoint, String apiKey, String body) {
        RestClient.RequestBodySpec request = client.post().uri(endpoint).contentType(MediaType.APPLICATION_JSON).body(body);
        if (apiKey != null && !apiKey.isBlank()) {
            request = request.header("Authorization", "Bearer " + apiKey);
        }
        return request;
    }
}
