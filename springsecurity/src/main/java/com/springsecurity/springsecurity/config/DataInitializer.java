package com.springsecurity.springsecurity.config;

import com.springsecurity.springsecurity.model.AppUser;
import com.springsecurity.springsecurity.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class DataInitializer {

    @Bean
    public CommandLineRunner initDatabase(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        return args -> {
            // Seed a regular user if not already in DB
            if (userRepository.findByUsername("user").isEmpty()) {
                userRepository.save(new AppUser(
                        "user",
                        passwordEncoder.encode("password123"),
                        "ROLE_USER"
                ));
            }

            // Seed an admin user if not already in DB
            if (userRepository.findByUsername("admin").isEmpty()) {
                userRepository.save(new AppUser(
                        "admin",
                        passwordEncoder.encode("admin123"),
                        "ROLE_ADMIN"
                ));
            }
        };
    }
}