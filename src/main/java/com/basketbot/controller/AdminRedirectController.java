package com.basketbot.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.net.URI;

/**
 * Редирект со старого пути /admin на новый UI (Next.js).
 */
@Controller
@RequestMapping("/admin")
public class AdminRedirectController {

    @Value("${app.admin-ui-url:http://localhost:3000}")
    private String adminUiUrl;

    @GetMapping
    public ResponseEntity<Void> redirectToAdminUi() {
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(adminUiUrl)).build();
    }

    @GetMapping("/**")
    public ResponseEntity<Void> redirectToAdminUiAny() {
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(adminUiUrl)).build();
    }
}
