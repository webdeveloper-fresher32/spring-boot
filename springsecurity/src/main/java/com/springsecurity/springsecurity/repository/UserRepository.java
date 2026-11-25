package com.springsecurity.springsecurity.repository;


import com.springsecurity.springsecurity.model.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<AppUser,Long> {

    // Spring Data JPA automatically generates SQL: SELECT * FROM app_users WHERE username = ?
    Optional<AppUser> findByUsername(String username);
}
