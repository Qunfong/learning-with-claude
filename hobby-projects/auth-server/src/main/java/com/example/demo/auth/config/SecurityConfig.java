package com.example.demo.auth.config;

import com.example.demo.auth.filter.BearerTokenAuthFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableScheduling
public class SecurityConfig {

    private final BearerTokenAuthFilter bearerTokenAuthFilter;

    public SecurityConfig(BearerTokenAuthFilter bearerTokenAuthFilter) {
        this.bearerTokenAuthFilter = bearerTokenAuthFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/oauth/**",
                    "/.well-known/**",
                    "/health",
                    "/admin/**",
                    "/h2-console/**"
                ).permitAll()
                .anyRequest().permitAll() // enforcement is handled in BearerTokenAuthFilter
            )
            .headers(headers -> headers.frameOptions(frame -> frame.disable())) // for H2 console
            .addFilterBefore(bearerTokenAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
