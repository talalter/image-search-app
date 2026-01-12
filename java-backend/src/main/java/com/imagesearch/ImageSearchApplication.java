package com.imagesearch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main entry point for the Image Search Backend application.
 *
 * This is a Spring Boot application that serves as the primary backend
 * for the image search system, orchestrating:
 * - User authentication and session management
 * - Folder and image metadata storage (PostgreSQL)
 * - Communication with Python search microservice (FAISS + CLIP)
 * - RESTful API for React frontend
 *
 * Architecture:
 * React Frontend → Java Backend (this) → Python Search Microservice
 *
 * @author Tal Alter
 */
@SpringBootApplication
@EnableScheduling
@EnableAsync  // Enable async method execution for background batch processing
public class ImageSearchApplication {

    public static void main(String[] args) {
        SpringApplication.run(ImageSearchApplication.class, args);
    }
}
