package com.imagesearch.search.health;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.cluster.HealthResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Spring Boot Health Indicator for Elasticsearch
 *
 * Checks if Elasticsearch cluster is reachable and reports health status.
 * Accessible via /actuator/health endpoint.
 */
@Component
@Slf4j
public class ElasticsearchHealthIndicator implements HealthIndicator {

    private final ElasticsearchClient client;

    public ElasticsearchHealthIndicator(ElasticsearchClient client) {
        this.client = client;
    }

    @Override
    public Health health() {
        try {
            // Check Elasticsearch cluster health
            HealthResponse response = client.cluster().health();

            String status = response.status().jsonValue();
            int numberOfNodes = response.numberOfNodes();
            String clusterName = response.clusterName();

            // Build health details
            Health.Builder builder;

            switch (status) {
                case "green":
                    builder = Health.up();
                    break;
                case "yellow":
                    builder = Health.up()
                        .withDetail("warning", "Elasticsearch cluster is yellow (some replicas not allocated)");
                    break;
                case "red":
                    builder = Health.down()
                        .withDetail("error", "Elasticsearch cluster is red (some primary shards not allocated)");
                    break;
                default:
                    builder = Health.unknown()
                        .withDetail("status", status);
            }

            return builder
                    .withDetail("cluster_name", clusterName)
                    .withDetail("status", status)
                    .withDetail("number_of_nodes", numberOfNodes)
                    .withDetail("timed_out", response.timedOut())
                    .build();

        } catch (Exception e) {
            log.error("Elasticsearch health check failed: {}", e.getMessage());
            return Health.down()
                    .withDetail("error", e.getMessage())
                    .withDetail("type", e.getClass().getSimpleName())
                    .build();
        }
    }
}
