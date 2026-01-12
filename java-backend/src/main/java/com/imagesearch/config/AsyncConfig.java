package com.imagesearch.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Configuration for async task execution.
 * 
 * Used for background processing of image embeddings during bulk uploads.
 * Allows upload API to return immediately while embeddings are generated in background.
 */
@Configuration
public class AsyncConfig {

    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        
        // Core pool size: number of threads to keep alive
        executor.setCorePoolSize(2);
        
        // Max pool size: maximum number of threads
        executor.setMaxPoolSize(5);
        
        // Queue capacity: how many tasks can wait before rejection
        executor.setQueueCapacity(100);
        
        // Thread name prefix for easier debugging
        executor.setThreadNamePrefix("async-embedding-");
        
        executor.initialize();
        return executor;
    }
    
    /**
     * Dedicated executor for image embedding tasks.
     * Alias for taskExecutor to support @Async("embeddingExecutor").
     */
    @Bean(name = "embeddingExecutor")
    public Executor embeddingExecutor() {
        return taskExecutor();
    }
}
