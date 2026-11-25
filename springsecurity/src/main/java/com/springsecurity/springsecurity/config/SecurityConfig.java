package com.springsecurity.springsecurity.config;


import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

// this class defines spring beans
@Configuration

// Activates Spring security's web support
@EnableWebSecurity

// @Bean SecurityFilterChain
// Replaces the old WebSecurityConfigurerAdapter (removed in Spring Security 6+)

public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws  Exception{
        http.authorizeHttpRequests(auth->auth.
                requestMatchers("/api/users/pulic").permitAll().
                requestMatchers("/api/users/admin").hasRole("ADMIN")
                .anyRequest().authenticated())
                .formLogin(Customizer.withDefaults())
                .httpBasic(Customizer.withDefaults());

        return  http.build();
    }

}
