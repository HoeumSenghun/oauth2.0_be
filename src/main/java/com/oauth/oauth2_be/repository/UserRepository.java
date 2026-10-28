package com.oauth.oauth2_be.repository;

import com.oauth.oauth2_be.entity.AuthProvider;
import com.oauth.oauth2_be.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByProviderAndProviderId(AuthProvider provider, String providerId);
}
