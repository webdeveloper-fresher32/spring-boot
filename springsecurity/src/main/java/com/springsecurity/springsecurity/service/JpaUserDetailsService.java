package com.springsecurity.springsecurity.service;

import com.springsecurity.springsecurity.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

// 1. @Service marks this as a Spring Bean so Spring Security discovers it automatically
@Service
public class JpaUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    // 2. Inject UserRepository via constructor
    public JpaUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * 3. Whenever someone submits the login form, Spring Security calls this method
     * with the username they typed. We look up the user in PostgreSQL.
     */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
    }
}