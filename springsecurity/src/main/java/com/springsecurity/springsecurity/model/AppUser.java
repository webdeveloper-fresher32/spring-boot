package com.springsecurity.springsecurity.model;

import jakarta.persistence.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

// 1. @Entity tells Hibernate to map this Java class to a PostgreSQL database table
@Entity
@Table(name = "app_users")
public class AppUser implements UserDetails {

    // 2. Primary key: auto-incremented by Postgres (IDENTITY)
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 3. Unique username column in Postgres (cannot have duplicate usernames)
    @Column(unique = true, nullable = false)
    private String username;

    // 4. Stores the BCrypt HASHED password (never plain text!)
    @Column(nullable = false)
    private String password;

    // 5. Stores the user's role (e.g. "ROLE_USER" or "ROLE_ADMIN")
    private String role;

    // Default constructor required by JPA/Hibernate
    public AppUser() {}

    // Convenience constructor for creating users
    public AppUser(String username, String password, String role) {
        this.username = username;
        this.password = password;
        this.role = role;
    }

    public Long getId() { return id; }
    public String getRole() { return role; }

    // =========================================================================
    // UserDetails Interface Methods: The Bridge Between DB and Spring Security
    // =========================================================================

    /**
     * Maps our DB "role" column to Spring Security's "GrantedAuthority".
     * Spring Security checks this list when evaluating .hasRole() or .hasAuthority().
     */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(role));
    }

    /**
     * Spring Security calls this to get the stored password hash from the DB,
     * so it can compare it against the raw password the user typed at login.
     */
    @Override
    public String getPassword() {
        return password;
    }

    /**
     * Spring Security calls this to know the user's unique identity.
     */
    @Override
    public String getUsername() {
        return username;
    }

    /**
     * Is the account expired? Return true = account is active/valid.
     * (If you add an expiration date column in DB, you can check that here).
     */
    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    /**
     * Is the account locked? (e.g. after 5 failed password attempts).
     * Return true = account is NOT locked.
     */
    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    /**
     * Are the user's credentials (password) expired?
     * Return true = password is valid and does not need a reset.
     */
    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    /**
     * Is the account enabled/verified? (e.g. email verified).
     * Return true = enabled.
     */
    @Override
    public boolean isEnabled() {
        return true;
    }
}