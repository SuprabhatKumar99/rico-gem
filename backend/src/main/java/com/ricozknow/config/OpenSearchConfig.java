package com.ricozknow.config;

import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.transport.OpenSearchTransport;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5TransportBuilder;
import org.apache.hc.core5.http.HttpHost;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenSearch is a derived store (spec section 23): it can be rebuilt from
 * PostgreSQL at any time, so this config intentionally has no retry/backoff
 * complexity here — that lives in the indexing worker (Phase 4).
 */
@Configuration
public class OpenSearchConfig {

    @Value("${opensearch.host}")
    private String host;

    @Value("${opensearch.port}")
    private int port;

    @Value("${opensearch.scheme}")
    private String scheme;

    @Bean
    public OpenSearchClient openSearchClient() {
        OpenSearchTransport transport = ApacheHttpClient5TransportBuilder
                .builder(new HttpHost(scheme, host, port))
                .build();
        return new OpenSearchClient(transport);
    }
}
