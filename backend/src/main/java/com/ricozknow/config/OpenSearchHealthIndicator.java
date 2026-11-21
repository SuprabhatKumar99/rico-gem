package com.ricozknow.config;

import org.opensearch.client.opensearch.OpenSearchClient;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("opensearch")
public class OpenSearchHealthIndicator implements HealthIndicator {
    private final OpenSearchClient client;
    public OpenSearchHealthIndicator(OpenSearchClient client) { this.client = client; }
    @Override
    public Health health() {
        try {
            var response = client.cluster().health();
            String status = response.status() == null ? "unknown" : response.status().toString();
            if ("red".equalsIgnoreCase(status)) {
                return Health.down().withDetail("clusterStatus", status).build();
            }
            return Health.up().withDetail("clusterStatus", status).withDetail("nodes", response.numberOfNodes()).build();
        } catch (Exception ex) {
            return Health.down(ex).withDetail("clusterStatus", "unreachable").build();
        }
    }
}
