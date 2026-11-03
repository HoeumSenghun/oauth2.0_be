package com.oauth.oauth2_be.service;

import com.oauth.oauth2_be.entity.AuthProvider;
import com.oauth.oauth2_be.entity.Role;
import com.oauth.oauth2_be.entity.User;
import com.oauth.oauth2_be.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

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

    @Transactional(readOnly = true)
    public List<User> listAll() {
        return userRepository.findAll();
    }

    @Transactional
    public User updateRole(Long id, Role role) {
        User user = getById(id);
        user.setRole(role);
        return user;
    }

    @Transactional
    public User registerLocal(String email, String rawPassword) {
        if (userRepository.findByProviderAndProviderId(AuthProvider.LOCAL, email).isPresent()) {
            throw new EmailAlreadyInUseException(email);
        }
        return userRepository.save(User.builder()
                .provider(AuthProvider.LOCAL)
                .providerId(email)
                .email(email)
                .passwordHash(passwordEncoder.encode(rawPassword))
                .build());
    }

    @Transactional(readOnly = true)
    public User authenticateLocal(String email, String rawPassword) {
        User user = userRepository.findByProviderAndProviderId(AuthProvider.LOCAL, email)
                .orElseThrow(InvalidCredentialsException::new);
        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        return user;
    }
}
