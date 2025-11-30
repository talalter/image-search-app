package com.imagesearch.config;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.lang.NonNull;

import java.util.HashMap;
import java.util.Map;

/**
 * Loads environment variables from .env file before Spring Boot initialization.
 * This allows application.yml to reference these variables using ${VAR_NAME}.
 * 
 * The .env file should be in the project root (java-backend/.env).
 */
public class DotenvConfig implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(@NonNull ConfigurableApplicationContext applicationContext) {
        try {
            // Load .env file from project root
            Dotenv dotenv = Dotenv.configure()
                    .directory(".")  // Look for .env in current directory
                    .ignoreIfMissing()  // Don't fail if .env doesn't exist (use system env vars)
                    .load();

            // Convert dotenv entries to a map
            Map<String, Object> dotenvMap = new HashMap<>();
            dotenv.entries().forEach(entry -> {
                dotenvMap.put(entry.getKey(), entry.getValue());
                // Also set as system property so Spring Boot can access it
                System.setProperty(entry.getKey(), entry.getValue());
            });

            // Add dotenv properties to Spring environment
            ConfigurableEnvironment environment = applicationContext.getEnvironment();
            environment.getPropertySources().addFirst(
                new MapPropertySource("dotenvProperties", dotenvMap)
            );

            System.out.println("✓ Loaded .env file with " + dotenvMap.size() + " variables");
        } catch (Exception e) {
            System.err.println("⚠ Warning: Could not load .env file: " + e.getMessage());
            System.err.println("  Falling back to system environment variables");
        }
    }
}
