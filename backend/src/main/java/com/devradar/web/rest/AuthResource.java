package com.devradar.web.rest;

import com.devradar.crypto.TokenEncryptor;
import com.devradar.domain.User;
import com.devradar.domain.UserGithubIdentity;
import com.devradar.github.GitHubApiClient;
import com.devradar.github.GitHubOAuthClient;
import com.devradar.repository.UserGithubIdentityRepository;
import com.devradar.repository.UserRepository;
import com.devradar.security.JwtTokenProvider;
import com.devradar.service.AuthService;
import com.devradar.web.rest.dto.*;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/auth")
public class AuthResource {

    private static final Logger LOG = LoggerFactory.getLogger(AuthResource.class);
    private static final java.time.Duration STATE_TTL = java.time.Duration.ofMinutes(10);

    private final AuthService auth;
    private final GitHubOAuthClient ghOauth;
    private final GitHubApiClient ghApi;
    private final UserRepository userRepo;
    private final UserGithubIdentityRepository identityRepo;
    private final TokenEncryptor encryptor;
    private final JwtTokenProvider jwt;
    private final String authorizeUrl;
    private final String defaultScopes;

    private final ConcurrentHashMap<String, Instant> issuedStates = new ConcurrentHashMap<>();

    public AuthResource(
        AuthService auth,
        GitHubOAuthClient ghOauth,
        GitHubApiClient ghApi,
        UserRepository userRepo,
        UserGithubIdentityRepository identityRepo,
        TokenEncryptor encryptor,
        JwtTokenProvider jwt,
        @Value("${github.oauth.authorize-url}") String authorizeUrl,
        @Value("${github.oauth.default-scopes:read:user,public_repo,repo}") String defaultScopes
    ) {
        this.auth = auth;
        this.ghOauth = ghOauth;
        this.ghApi = ghApi;
        this.userRepo = userRepo;
        this.identityRepo = identityRepo;
        this.encryptor = encryptor;
        this.jwt = jwt;
        this.authorizeUrl = authorizeUrl;
        this.defaultScopes = defaultScopes;
    }

    @PostMapping("/register")
    public ResponseEntity<Void> register(@Valid @RequestBody RegisterRequestDTO body) {
        auth.register(body.email(), body.password(), body.displayName());
        return ResponseEntity.status(201).build();
    }

    @PostMapping("/login")
    public LoginResponseDTO login(@Valid @RequestBody LoginRequestDTO body) {
        var r = auth.login(body.email(), body.password());
        return new LoginResponseDTO(r.accessToken(), r.refreshToken());
    }

    @PostMapping("/refresh")
    public LoginResponseDTO refresh(@Valid @RequestBody RefreshRequestDTO body) {
        var r = auth.refresh(body.refreshToken());
        return new LoginResponseDTO(r.accessToken(), r.refreshToken());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequestDTO body) {
        auth.logout(body.refreshToken());
        return ResponseEntity.noContent().build();
    }

    /** Step 1 of GitHub OAuth: redirect user to GitHub consent. */
    @GetMapping("/github/start")
    public ResponseEntity<Void> githubStart() {
        purgeExpiredStates();
        String state = GitHubOAuthClient.generateState();
        issuedStates.put(state, Instant.now().plus(STATE_TTL));
        String url = ghOauth.buildAuthorizeUrl(authorizeUrl, state, defaultScopes);
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(url)).build();
    }

    /** Step 2 of GitHub OAuth: exchange code, persist identity, issue JWT. */
    @GetMapping("/github/callback")
    @Transactional
    public LoginResponseDTO githubCallback(@RequestParam String code, @RequestParam String state) {
        Instant expires = issuedStates.remove(state);
        if (expires == null || expires.isBefore(Instant.now())) {
            throw new RuntimeException("invalid or expired state");
        }

        var tokenResp = ghOauth.exchangeCode(code);
        var ghUser = ghApi.getAuthenticatedUser(tokenResp.accessToken());

        Optional<UserGithubIdentity> existing = identityRepo.findByGithubUserId(ghUser.id());
        User u;
        UserGithubIdentity identity;
        if (existing.isPresent()) {
            identity = existing.get();
            u = userRepo.findById(identity.getUserId()).orElseThrow();
            identity.setAccessTokenEncrypted(encryptor.encrypt(tokenResp.accessToken()));
            identity.setGrantedScopes(tokenResp.grantedScopes());
            identityRepo.save(identity);
        } else {
            String email = ghUser.login() + "@github.users.noreply.devradar";
            u = new User();
            u.setEmail(email);
            u.setDisplayName(ghUser.login());
            u.setActive(true);
            u = userRepo.save(u);

            identity = new UserGithubIdentity();
            identity.setUserId(u.getId());
            identity.setGithubUserId(ghUser.id());
            identity.setGithubLogin(ghUser.login());
            identity.setAccessTokenEncrypted(encryptor.encrypt(tokenResp.accessToken()));
            identity.setGrantedScopes(tokenResp.grantedScopes());
            identityRepo.save(identity);
        }

        String jwtToken = jwt.generateAccessToken(u.getId(), u.getEmail());
        return new LoginResponseDTO(jwtToken, "");
    }

    private void purgeExpiredStates() {
        Instant now = Instant.now();
        issuedStates.entrySet().removeIf(e -> e.getValue().isBefore(now));
    }
}
