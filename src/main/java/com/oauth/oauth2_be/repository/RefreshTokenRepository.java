package com.oauth.oauth2_be.repository;

import com.oauth.oauth2_be.entity.RefreshToken;
import com.oauth.oauth2_be.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    void deleteAllByUser(User user);
}
