package com.devradar.service;

import com.devradar.domain.User;
import com.devradar.domain.exception.UserAlreadyExistsException;
import com.devradar.repository.RefreshTokenRepository;
import com.devradar.repository.UserRepository;
import com.devradar.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AuthServiceTest {

    UserRepository userRepo;
    RefreshTokenRepository refreshRepo;
    JwtTokenProvider jwt;
    AuthService service;

    @BeforeEach
    void setup() {
        userRepo = mock(UserRepository.class);
        refreshRepo = mock(RefreshTokenRepository.class);
        jwt = mock(JwtTokenProvider.class);
        AuditLogService audit = mock(AuditLogService.class);
        service = new AuthService(userRepo, refreshRepo, new BCryptPasswordEncoder(12), jwt, audit, 1);
    }

    @Test
    void register_persistsUserWithHashedPassword() {
        when(userRepo.existsByEmail("a@b.c")).thenReturn(false);
        when(userRepo.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            // simulate ID assignment
            try { var f = User.class.getDeclaredField("id"); f.setAccessible(true); f.set(u, 1L); } catch (Exception e) {}
            return u;
        });

        User created = service.register("a@b.c", "Password1!", "Alice");

        assertThat(created.getEmail()).isEqualTo("a@b.c");
        assertThat(created.getDisplayName()).isEqualTo("Alice");
        assertThat(created.getPasswordHash()).isNotEqualTo("Password1!");
        assertThat(created.getPasswordHash()).startsWith("$2");
    }

    @Test
    void register_throwsWhenEmailExists() {
        when(userRepo.existsByEmail("a@b.c")).thenReturn(true);
        assertThatThrownBy(() -> service.register("a@b.c", "x", "x"))
            .isInstanceOf(UserAlreadyExistsException.class);
    }

    @Test
    void login_returnsTokens_whenCredentialsValid() {
        User u = makeStoredUser("a@b.c", "Password1!");
        when(userRepo.findByEmail("a@b.c")).thenReturn(Optional.of(u));
        when(jwt.generateAccessToken(1L, "a@b.c")).thenReturn("ACCESS");

        AuthService.AuthResult r = service.login("a@b.c", "Password1!");

        assertThat(r.accessToken()).isEqualTo("ACCESS");
        assertThat(r.refreshToken()).isNotBlank();
        verify(refreshRepo).save(any());
    }

    @Test
    void login_throws_whenPasswordWrong() {
        User u = makeStoredUser("a@b.c", "RealPassword!");
        when(userRepo.findByEmail("a@b.c")).thenReturn(Optional.of(u));
        assertThatThrownBy(() -> service.login("a@b.c", "Wrong"))
            .isInstanceOf(com.devradar.domain.exception.InvalidCredentialsException.class);
    }

    @Test
    void login_throws_whenUserNotFound() {
        when(userRepo.findByEmail("nope@b.c")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.login("nope@b.c", "x"))
            .isInstanceOf(com.devradar.domain.exception.InvalidCredentialsException.class);
    }

    private User makeStoredUser(String email, String plainPassword) {
        User u = new User();
        u.setEmail(email);
        u.setDisplayName("Alice");
        u.setPasswordHash(new BCryptPasswordEncoder(12).encode(plainPassword));
        try { var f = User.class.getDeclaredField("id"); f.setAccessible(true); f.set(u, 1L); } catch (Exception e) {}
        u.setActive(true);
        return u;
    }
}
