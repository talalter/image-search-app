package com.imagesearch.search.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Elasticsearch Client Configuration
 *
 * Creates and configures the Elasticsearch Java client for vector search operations.
 * Uses the new Elasticsearch Java API Client (8.x) which provides type-safe APIs.
 */
@Configuration
@Slf4j
public class ElasticsearchConfig {

    @Value("${elasticsearch.host:localhost}")
    private String host;

    @Value("${elasticsearch.port:9200}")
    private int port;

    @Value("${elasticsearch.scheme:http}")
    private String scheme;

    /**
     * Create RestClient for low-level HTTP communication
     */
    @Bean
    public RestClient restClient() {
        log.info("Initializing Elasticsearch RestClient: {}://{}:{}", scheme, host, port);
        return RestClient.builder(new HttpHost(host, port, scheme)).build();
    }

    /**
     * Create ElasticsearchTransport with Jackson JSON mapper
     */
    @Bean
    public ElasticsearchTransport elasticsearchTransport(RestClient restClient) {
        log.info("Creating Elasticsearch transport with Jackson JSON mapper");
        return new RestClientTransport(restClient, new JacksonJsonpMapper());
    }

    /**
     * Create ElasticsearchClient (main API client)
     */
    @Bean
    public ElasticsearchClient elasticsearchClient(ElasticsearchTransport transport) {
        log.info("Creating Elasticsearch client");
        return new ElasticsearchClient(transport);
    }
}
