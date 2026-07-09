package com.pontalti.game2048.adapter.rest.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Enables CORS for the browser-based web UI.
 * <p>
 * The React client runs on a different origin (e.g. a dev server on port 5173 or
 * a static server on 8000) than this service (8080). Browsers block cross-origin
 * calls unless the server opts in, so this config allows the game endpoints to be
 * called from the UI's origin. Adjust the allowed origins to match wherever the
 * UI is actually served from.
 * <p>
 * This lives in the REST adapter layer, alongside {@code BeanConfig}: it is an
 * inbound-transport concern, not a domain concern, so the game core stays
 * unaware of it.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/games/**")
                .allowedOrigins(
                        "http://localhost:5173",  // Vite dev server
                        "http://localhost:3000",  // Create React App / Next dev
                        "http://localhost:8000",  // python -m http.server, etc.
                        "http://127.0.0.1:5500"   // VS Code Live Server
                )
                .allowedMethods("GET", "POST", "DELETE")
                .allowedHeaders("*");
    }
}