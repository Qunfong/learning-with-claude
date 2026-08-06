package com.bookscanner.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS configuratie zodat de Angular frontend (port 3000/4200) de backend
 * (port 8080) mag aanroepen.
 *
 * Leerpunt: In productie zou je origins beperken tot je domein.
 * Voor lokale development staat '*' toe, maar dat is te breed voor productie.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins("http://localhost:3000", "http://localhost:4200")
                .allowedMethods("GET", "POST", "DELETE", "OPTIONS")
                .allowedHeaders("*");
    }
}
