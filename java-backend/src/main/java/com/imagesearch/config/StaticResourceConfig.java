package com.imagesearch.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Configuration for serving static image files.
 *
 * Maps /images/** URLs to the filesystem directory where uploaded images are stored.
 * This allows the frontend to access images via HTTP.
 */
@Configuration
public class StaticResourceConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(@NonNull ResourceHandlerRegistry registry) {
        // Get absolute path to data/uploads/images directory in project root
        Path currentDir = Paths.get("").toAbsolutePath();
        Path projectRoot = currentDir.getFileName().toString().equals("java-backend")
            ? currentDir.getParent()
            : currentDir;
        Path imagesPath = projectRoot.resolve("data").resolve("uploads").resolve("images");

        // Ensure the path ends with separator for proper resource resolution
        String imagesLocation = "file:" + imagesPath.toAbsolutePath() + "/";

        // Map /images/** URLs to the filesystem data/uploads/images directory
        registry.addResourceHandler("/images/**")
                .addResourceLocations(imagesLocation);
    }
}
