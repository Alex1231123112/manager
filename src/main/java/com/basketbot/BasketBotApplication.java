package com.basketbot;

import com.basketbot.config.AdminSecurityProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class BasketBotApplication {

    private static final Logger log = LoggerFactory.getLogger(BasketBotApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(BasketBotApplication.class, args);
    }

    @Bean
    ApplicationRunner logAdminUser(AdminSecurityProperties adminProps) {
        return args -> log.info("Admin login: {} (пароль из конфига)", adminProps.getUsername());
    }
}
