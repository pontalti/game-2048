package com.pontalti.game2048;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot entry point for the 2048 REST service.
 * <p>
 * Note this replaces the old desktop {@code Main}: the application is now a web
 * service. The domain it drives is byte-for-byte the same as the desktop
 * version — only the inbound adapter changed from keyboard to HTTP.
 */
@SpringBootApplication
public class Game2048Application {
    public static void main(String[] args) {
        SpringApplication.run(Game2048Application.class, args);
    }
}
