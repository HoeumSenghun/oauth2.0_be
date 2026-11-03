package com.oauth.oauth2_be.service;

import com.oauth.oauth2_be.entity.AuthProvider;
import com.oauth.oauth2_be.entity.Role;
import com.oauth.oauth2_be.entity.User;
import com.oauth.oauth2_be.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, passwordEncoder);
    }

    @Test
    void findOrCreateCreatesNewUserWhenNoneExists() {
        when(userRepository.findByProviderAndProviderId(AuthProvider.GOOGLE, "sub-1"))
                .thenReturn(Optional.empty());
        User saved = User.builder()
                .id(1L)
                .provider(AuthProvider.GOOGLE)
                .providerId("sub-1")
                .email("new@example.com")
                .name("New User")
                .avatarUrl("https://example.com/new.png")
                .build();
        when(userRepository.save(org.mockito.ArgumentMatchers.any(User.class))).thenReturn(saved);

        User result = userService.findOrCreate(
                AuthProvider.GOOGLE, "sub-1", "new@example.com", "New User", "https://example.com/new.png"
        );

        assertThat(result.getId()).isEqualTo(1L);
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getProvider()).isEqualTo(AuthProvider.GOOGLE);
        assertThat(captor.getValue().getProviderId()).isEqualTo("sub-1");
        assertThat(captor.getValue().getEmail()).isEqualTo("new@example.com");
    }

    @Test
    void findOrCreateUpdatesExistingUserProfileFields() {
        User existing = User.builder()
                .id(5L)
                .provider(AuthProvider.GITHUB)
                .providerId("gh-1")
                .email("old@example.com")
                .name("Old Name")
                .avatarUrl("https://example.com/old.png")
                .build();
        when(userRepository.findByProviderAndProviderId(AuthProvider.GITHUB, "gh-1"))
                .thenReturn(Optional.of(existing));

        User result = userService.findOrCreate(
                AuthProvider.GITHUB, "gh-1", "updated@example.com", "Updated Name", "https://example.com/updated.png"
        );

        assertThat(result.getId()).isEqualTo(5L);
        assertThat(result.getEmail()).isEqualTo("updated@example.com");
        assertThat(result.getName()).isEqualTo("Updated Name");
        assertThat(result.getAvatarUrl()).isEqualTo("https://example.com/updated.png");
        verify(userRepository, org.mockito.Mockito.never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void getByIdReturnsUserWhenFound() {
        User user = User.builder().id(9L).build();
        when(userRepository.findById(9L)).thenReturn(Optional.of(user));

        assertThat(userService.getById(9L)).isSameAs(user);
    }

    @Test
    void getByIdThrowsUserNotFoundExceptionWhenMissing() {
        when(userRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getById(404L))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessageContaining("404");
    }

    @Test
    void listAllReturnsEveryUser() {
        User a = User.builder().id(1L).build();
        User b = User.builder().id(2L).build();
        when(userRepository.findAll()).thenReturn(List.of(a, b));

        assertThat(userService.listAll()).containsExactly(a, b);
    }

    @Test
    void updateRolePersistsNewRole() {
        User user = User.builder().id(10L).role(Role.USER).build();
        when(userRepository.findById(10L)).thenReturn(Optional.of(user));

        User result = userService.updateRole(10L, Role.ADMIN);

        assertThat(result.getRole()).isEqualTo(Role.ADMIN);
        assertThat(user.getRole()).isEqualTo(Role.ADMIN);
    }

    @Test
    void updateRoleThrowsUserNotFoundExceptionWhenMissing() {
        when(userRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.updateRole(404L, Role.ADMIN))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void registerLocalCreatesUserWithHashedPassword() {
        when(userRepository.findByProviderAndProviderId(AuthProvider.LOCAL, "new@example.com"))
                .thenReturn(Optional.empty());
        when(userRepository.save(org.mockito.ArgumentMatchers.any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        User result = userService.registerLocal("new@example.com", "super-secret");

        assertThat(result.getProvider()).isEqualTo(AuthProvider.LOCAL);
        assertThat(result.getProviderId()).isEqualTo("new@example.com");
        assertThat(result.getEmail()).isEqualTo("new@example.com");
        assertThat(result.getPasswordHash()).isNotEqualTo("super-secret");
        assertThat(passwordEncoder.matches("super-secret", result.getPasswordHash())).isTrue();
    }

    @Test
    void registerLocalThrowsWhenEmailAlreadyInUse() {
        when(userRepository.findByProviderAndProviderId(AuthProvider.LOCAL, "taken@example.com"))
                .thenReturn(Optional.of(User.builder().id(1L).build()));

        assertThatThrownBy(() -> userService.registerLocal("taken@example.com", "super-secret"))
                .isInstanceOf(EmailAlreadyInUseException.class);
    }

    @Test
    void authenticateLocalReturnsUserWhenPasswordMatches() {
        User user = User.builder()
                .id(2L)
                .provider(AuthProvider.LOCAL)
                .providerId("ada@example.com")
                .email("ada@example.com")
                .passwordHash(passwordEncoder.encode("correct-password"))
                .build();
        when(userRepository.findByProviderAndProviderId(AuthProvider.LOCAL, "ada@example.com"))
                .thenReturn(Optional.of(user));

        assertThat(userService.authenticateLocal("ada@example.com", "correct-password")).isSameAs(user);
    }

    @Test
    void authenticateLocalThrowsWhenPasswordDoesNotMatch() {
        User user = User.builder()
                .id(3L)
                .provider(AuthProvider.LOCAL)
                .providerId("grace@example.com")
                .email("grace@example.com")
                .passwordHash(passwordEncoder.encode("correct-password"))
                .build();
        when(userRepository.findByProviderAndProviderId(AuthProvider.LOCAL, "grace@example.com"))
                .thenReturn(Optional.of(user));

        assertThatThrownBy(() -> userService.authenticateLocal("grace@example.com", "wrong-password"))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void authenticateLocalThrowsWhenEmailUnknown() {
        when(userRepository.findByProviderAndProviderId(AuthProvider.LOCAL, "ghost@example.com"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.authenticateLocal("ghost@example.com", "whatever"))
                .isInstanceOf(InvalidCredentialsException.class);
    }
}
