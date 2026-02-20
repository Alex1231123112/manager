package com.basketbot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Component
@ConfigurationProperties(prefix = "app.cors")
public class CorsProperties {

    /**
     * Разрешённые origin для CORS (через запятую в одной строке или список в YAML).
     * По умолчанию http://localhost:3000.
     */
    private String allowedOrigins = "http://localhost:3000";

    public String getAllowedOrigins() {
        return allowedOrigins;
    }

    public void setAllowedOrigins(String allowedOrigins) {
        this.allowedOrigins = allowedOrigins != null ? allowedOrigins.trim() : "";
    }

    /** Возвращает список origin (разделитель — запятая). Пустые строки отфильтровываются. */
    public List<String> getAllowedOriginsList() {
        if (allowedOrigins == null || allowedOrigins.isBlank()) {
            return List.of("http://localhost:3000");
        }
        return Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }
}
