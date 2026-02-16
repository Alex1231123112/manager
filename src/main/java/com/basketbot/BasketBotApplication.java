package com.basketbot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class BasketBotApplication {

    public static void main(String[] args) {
        SpringApplication.run(BasketBotApplication.class, args);
    }
}
