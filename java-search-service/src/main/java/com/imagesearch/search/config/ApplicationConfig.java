package com.imagesearch.search.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;

/**
 * Main Application Configuration.
 *
 * Best Practice: Central configuration class to enable Spring Boot features
 * and register configuration properties.
 */
@Configuration
@EnableCaching
@EnableConfigurationProperties(ClipModelProperties.class)
public class ApplicationConfig {
    // Configuration is handled via @ConfigurationProperties and @Component scanning
}
