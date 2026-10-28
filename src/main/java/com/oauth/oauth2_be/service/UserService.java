package com.oauth.oauth2_be.service;

import com.oauth.oauth2_be.entity.AuthProvider;
import com.oauth.oauth2_be.entity.User;
import com.oauth.oauth2_be.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    @Transactional
    public User findOrCreate(AuthProvider provider, String providerId, String email, String name, String avatarUrl) {
        return userRepository.findByProviderAndProviderId(provider, providerId)
                .map(existing -> {
                    existing.setName(name);
                    existing.setAvatarUrl(avatarUrl);
                    existing.setEmail(email);
                    return existing;
                })
                .orElseGet(() -> userRepository.save(User.builder()
                        .provider(provider)
                        .providerId(providerId)
                        .email(email)
                        .name(name)
                        .avatarUrl(avatarUrl)
                        .build()));
    }

    @Transactional(readOnly = true)
    public User getById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));
    }
}
