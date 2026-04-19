package com.devradar.service;

import com.devradar.domain.RefreshToken;
import com.devradar.domain.User;
import com.devradar.domain.exception.InvalidCredentialsException;
import com.devradar.domain.exception.InvalidRefreshTokenException;
import com.devradar.domain.exception.UserAlreadyExistsException;
import com.devradar.repository.RefreshTokenRepository;
import com.devradar.repository.UserRepository;
import com.devradar.security.JwtTokenProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository userRepo;
    private final RefreshTokenRepository refreshRepo;
    private final PasswordEncoder encoder;
    private final JwtTokenProvider jwt;
    private final AuditLogService audit;
    private final long refreshTtlDays;

    public AuthService(
        UserRepository userRepo,
        RefreshTokenRepository refreshRepo,
        PasswordEncoder encoder,
        JwtTokenProvider jwt,
        AuditLogService audit,
        @Value("${jwt.refresh-token-ttl-days}") long refreshTtlDays
    ) {
        this.userRepo = userRepo;
        this.refreshRepo = refreshRepo;
        this.encoder = encoder;
        this.jwt = jwt;
        this.audit = audit;
        this.refreshTtlDays = refreshTtlDays;
    }

    public record AuthResult(String accessToken, String refreshToken) {}

    @Transactional
    public User register(String email, String password, String displayName) {
        if (userRepo.existsByEmail(email)) throw new UserAlreadyExistsException(email);
        User user = new User();
        user.setEmail(email);
        user.setDisplayName(displayName);
        user.setPasswordHash(encoder.encode(password));
        User saved = userRepo.save(user);
        audit.log(saved.getId(), "USER_REGISTERED", "user", String.valueOf(saved.getId()), null);
        return saved;
    }

    @Transactional
    public AuthResult login(String email, String password) {
        User u = userRepo.findByEmail(email).orElseThrow(InvalidCredentialsException::new);
        if (!u.isActive() || !encoder.matches(password, u.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        AuthResult r = issueTokens(u);
        audit.log(u.getId(), "USER_LOGIN", "user", String.valueOf(u.getId()), null);
        return r;
    }

    @Transactional
    public AuthResult refresh(String rawRefreshToken) {
        String hash = sha256(rawRefreshToken);
        RefreshToken rt = refreshRepo.findByTokenHash(hash).orElseThrow(InvalidRefreshTokenException::new);
        if (!rt.isActive()) throw new InvalidRefreshTokenException();
        refreshRepo.revokeByTokenHash(hash, Instant.now());
        User u = userRepo.findById(rt.getUserId()).orElseThrow(InvalidRefreshTokenException::new);
        return issueTokens(u);
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        refreshRepo.revokeByTokenHash(sha256(rawRefreshToken), Instant.now());
        audit.log(null, "USER_LOGOUT", null, null, null);
    }

    private AuthResult issueTokens(User u) {
        String access = jwt.generateAccessToken(u.getId(), u.getEmail());
        String raw = UUID.randomUUID().toString() + "-" + UUID.randomUUID();
        String hash = sha256(raw);
        RefreshToken rt = new RefreshToken();
        rt.setUserId(u.getId());
        rt.setTokenHash(hash);
        rt.setExpiresAt(Instant.now().plus(refreshTtlDays, ChronoUnit.DAYS));
        refreshRepo.save(rt);
        return new AuthResult(access, raw);
    }

    private static String sha256(String input) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) { throw new RuntimeException(e); }
    }
}
