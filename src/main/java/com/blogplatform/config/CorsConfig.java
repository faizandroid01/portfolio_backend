package com.blogplatform.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/*
Issue - allowedOrigins cannot contain the special value "*" since that cannot be set on the "Access-Control-Allow-Origin" response header
Sol - If the browser allowed * + allowCredentials, a malicious site could send a request to your API and the browser would automatically include your user's session cookies. This would make it incredibly easy for hackers to perform actions on behalf of your users (CSRF attacks). By forcing you to list the origins, the browser ensures it only sends those cookies to sites you actually trust.*/

@Configuration
public class CorsConfig {

    @Value("${cors.allowed-origins}")
    private String allowedOrigins;

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {

                // Split and trim to remove any accidental spaces after commas
                String[] origins = allowedOrigins.split("\\s*,\\s*");

                registry.addMapping("/api/**")
                    .allowedOriginPatterns(origins)
                    .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                    .allowedHeaders("*")
                    .allowCredentials(true)
                    .maxAge(3600);
            }
        };
    }
}
