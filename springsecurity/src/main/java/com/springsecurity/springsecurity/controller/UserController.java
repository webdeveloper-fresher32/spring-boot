package com.springsecurity.springsecurity.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {

    @GetMapping
    public String getAllUser() {
        return "Hello World!";
    }

    // public end point - no login needed
    @GetMapping("/public")
    public String publicEndPoint() {
        return "This is PUBLIC - no login needed";
    }

    // Admin only end point
    @GetMapping("/admin")
    public String adminEndPoint() {
        return "This is ADMIN ONLY";
    }

}
