package com.devradar.service;

import com.devradar.domain.User;
import com.devradar.domain.exception.UserAlreadyExistsException;
import com.devradar.repository.RefreshTokenRepository;
import com.devradar.repository.UserRepository;
import com.devradar.security.JwtTokenProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepo;
    private final RefreshTokenRepository refreshRepo;
    private final PasswordEncoder encoder;
    private final JwtTokenProvider jwt;
    private final long refreshTtlDays;

    public AuthService(
        UserRepository userRepo,
        RefreshTokenRepository refreshRepo,
        PasswordEncoder encoder,
        JwtTokenProvider jwt,
        @Value("${jwt.refresh-token-ttl-days}") long refreshTtlDays
    ) {
        this.userRepo = userRepo;
        this.refreshRepo = refreshRepo;
        this.encoder = encoder;
        this.jwt = jwt;
        this.refreshTtlDays = refreshTtlDays;
    }

    @Transactional
    public User register(String email, String password, String displayName) {
        if (userRepo.existsByEmail(email)) {
            throw new UserAlreadyExistsException(email);
        }
        User user = new User();
        user.setEmail(email);
        user.setDisplayName(displayName);
        user.setPasswordHash(encoder.encode(password));
        return userRepo.save(user);
    }
}
