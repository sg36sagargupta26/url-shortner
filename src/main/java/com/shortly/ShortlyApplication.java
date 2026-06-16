package com.shortly;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the URL Shortener application.
 *
 * <p>Enables:
 * <ul>
 *   <li>Spring Boot auto-configuration</li>
 *   <li>Component scanning from {@code com.shortly} package</li>
 *   <li>Reactive web stack (WebFlux)</li>
 *   <li>R2DBC and Redis reactive data access</li>
 * </ul>
 */
@SpringBootApplication
public class ShortlyApplication {

    /**
     * Bootstraps the Spring Boot application.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(ShortlyApplication.class, args);
    }
}
