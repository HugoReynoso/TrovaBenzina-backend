package it.trovabenzina.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security")
public record SecurityProperties(String jwtSecret, long jwtExpirationMinutes, String adminEmail, String adminPassword) {
}
