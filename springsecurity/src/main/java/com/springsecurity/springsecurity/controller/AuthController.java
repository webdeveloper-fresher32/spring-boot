package com.springsecurity.springsecurity.controller;


import com.springsecurity.springsecurity.dto.LogInRequest;
import com.springsecurity.springsecurity.dto.RegisterRequest;
import com.springsecurity.springsecurity.model.AppUser;
import com.springsecurity.springsecurity.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    public AuthController(UserRepository userRepository,
                          PasswordEncoder passwordEncoder,
                          AuthenticationManager authenticationManager) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
    }
    // ==========================================
    // 1. REGISTER ENDPOINT
    // ==========================================

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest request) {
        // Check if username already exists
        if (userRepository.findByUsername(request.getUsername()).isPresent()) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Username already taken!"));
        }
        // Default to ROLE_USER if no role provided
        String role = (request.getRole() != null && !request.getRole().isBlank())
                ? request.getRole()
                : "ROLE_USER";
        // Save new user with HASHED password in PostgreSQL
        AppUser newUser = new AppUser(
                request.getUsername(),
                passwordEncoder.encode(request.getPassword()), // BCrypt encoding!
                role
        );
        userRepository.save(newUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "message", "User registered successfully!",
                "username", newUser.getUsername(),
                "role", newUser.getRole()
        ));
    }

    // ==========================================
    // 2. LOGIN ENDPOINT
    // ==========================================
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LogInRequest request) {
        try {
            // AuthenticationManager internally calls JpaUserDetailsService + PasswordEncoder.matches()
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
            );
            // Store authentication in the SecurityContext
            SecurityContextHolder.getContext().setAuthentication(authentication);
            return ResponseEntity.ok(Map.of(
                    "message", "Login successful!",
                    "username", authentication.getName(),
                    "authorities", authentication.getAuthorities()
            ));
        } catch (Exception ex) {
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid username or password!"));
        }
    }
}
