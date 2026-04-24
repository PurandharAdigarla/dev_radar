package com.devradar.web.rest;

import com.devradar.crypto.TokenEncryptor;
import com.devradar.domain.OAuthState;
import com.devradar.domain.User;
import com.devradar.domain.UserGithubIdentity;
import com.devradar.github.GitHubApiClient;
import com.devradar.github.GitHubOAuthClient;
import com.devradar.repository.OAuthStateRepository;
import com.devradar.repository.UserGithubIdentityRepository;
import com.devradar.repository.UserRepository;
import com.devradar.security.JwtTokenProvider;
import com.devradar.service.AuthService;
import com.devradar.web.rest.dto.*;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.Instant;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
public class AuthResource {

    private static final java.time.Duration STATE_TTL = java.time.Duration.ofMinutes(10);

    private final AuthService auth;
    private final GitHubOAuthClient ghOauth;
    private final GitHubApiClient ghApi;
    private final UserRepository userRepo;
    private final UserGithubIdentityRepository identityRepo;
    private final OAuthStateRepository oauthStateRepo;
    private final TokenEncryptor encryptor;
    private final JwtTokenProvider jwt;
    private final String authorizeUrl;
    private final String defaultScopes;
    private final String frontendBaseUrl;

    public AuthResource(
        AuthService auth,
        GitHubOAuthClient ghOauth,
        GitHubApiClient ghApi,
        UserRepository userRepo,
        UserGithubIdentityRepository identityRepo,
        OAuthStateRepository oauthStateRepo,
        TokenEncryptor encryptor,
        JwtTokenProvider jwt,
        @Value("${github.oauth.authorize-url}") String authorizeUrl,
        @Value("${github.oauth.default-scopes:read:user,public_repo,repo}") String defaultScopes,
        @Value("${frontend.base-url:http://localhost:5173}") String frontendBaseUrl
    ) {
        this.auth = auth;
        this.ghOauth = ghOauth;
        this.ghApi = ghApi;
        this.userRepo = userRepo;
        this.identityRepo = identityRepo;
        this.oauthStateRepo = oauthStateRepo;
        this.encryptor = encryptor;
        this.jwt = jwt;
        this.authorizeUrl = authorizeUrl;
        this.defaultScopes = defaultScopes;
        this.frontendBaseUrl = frontendBaseUrl;
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

    /** Step 1 of GitHub OAuth: redirect user to GitHub consent (login/signup). */
    @GetMapping("/github/start")
    public ResponseEntity<Void> githubStart() {
        return startOAuth(null);
    }

    /** Step 1 of GitHub OAuth: redirect user to GitHub consent (link to existing account). */
    @GetMapping("/github/link")
    public ResponseEntity<Void> githubLink(@RequestParam String token) {
        var details = jwt.parseAccessToken(token);
        if (details == null) {
            return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(frontendBaseUrl + "/app/settings?error=invalid_token"))
                .build();
        }
        return startOAuth(details.userId());
    }

    private ResponseEntity<Void> startOAuth(Long linkUserId) {
        oauthStateRepo.deleteByExpiresAtBefore(Instant.now());
        String state = GitHubOAuthClient.generateState();
        oauthStateRepo.save(new OAuthState(state, Instant.now().plus(STATE_TTL), linkUserId));
        String url = ghOauth.buildAuthorizeUrl(authorizeUrl, state, defaultScopes);
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(url)).build();
    }

    /** Step 2 of GitHub OAuth: exchange code, persist identity, issue JWT. */
    @GetMapping("/github/callback")
    @Transactional
    public ResponseEntity<Void> githubCallback(@RequestParam String code, @RequestParam String state) {
        Optional<OAuthState> opt = oauthStateRepo.findById(state);
        if (opt.isEmpty() || opt.get().getExpiresAt().isBefore(Instant.now())) {
            opt.ifPresent(oauthStateRepo::delete);
            return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(frontendBaseUrl + "/login?error=oauth_state"))
                .build();
        }
        OAuthState pending = opt.get();
        oauthStateRepo.delete(pending);

        var tokenResp = ghOauth.exchangeCode(code);
        var ghUser = ghApi.getAuthenticatedUser(tokenResp.accessToken());
        String encryptedToken = encryptor.encrypt(tokenResp.accessToken());

        if (pending.getLinkUserId() != null) {
            return handleLink(pending.getLinkUserId(), ghUser, encryptedToken, tokenResp.grantedScopes());
        }
        return handleLoginOrSignup(ghUser, encryptedToken, tokenResp.grantedScopes());
    }

    private ResponseEntity<Void> handleLink(Long userId, GitHubApiClient.AuthedUser ghUser,
                                             String encryptedToken, String scopes) {
        Optional<UserGithubIdentity> existing = identityRepo.findByGithubUserId(ghUser.id());
        if (existing.isPresent() && !existing.get().getUserId().equals(userId)) {
            return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(frontendBaseUrl + "/settings?error=github_already_linked"))
                .build();
        }

        User u = userRepo.findById(userId).orElseThrow();
        UserGithubIdentity identity = existing.orElseGet(() -> {
            var id = new UserGithubIdentity();
            id.setUserId(userId);
            id.setGithubUserId(ghUser.id());
            id.setGithubLogin(ghUser.login());
            return id;
        });
        identity.setAccessTokenEncrypted(encryptedToken);
        identity.setGrantedScopes(scopes);
        identityRepo.save(identity);

        String jwtToken = jwt.generateAccessToken(u.getId(), u.getEmail());
        return ResponseEntity.status(HttpStatus.FOUND)
            .location(URI.create(frontendBaseUrl + "/auth/github/complete?from=link#accessToken=" + jwtToken))
            .build();
    }

    private ResponseEntity<Void> handleLoginOrSignup(GitHubApiClient.AuthedUser ghUser,
                                                      String encryptedToken, String scopes) {
        Optional<UserGithubIdentity> existing = identityRepo.findByGithubUserId(ghUser.id());
        User u;
        UserGithubIdentity identity;
        if (existing.isPresent()) {
            identity = existing.get();
            u = userRepo.findById(identity.getUserId()).orElseThrow();
            identity.setAccessTokenEncrypted(encryptedToken);
            identity.setGrantedScopes(scopes);
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
            identity.setAccessTokenEncrypted(encryptedToken);
            identity.setGrantedScopes(scopes);
            identityRepo.save(identity);
        }

        String jwtToken = jwt.generateAccessToken(u.getId(), u.getEmail());
        String target = frontendBaseUrl + "/auth/github/complete#accessToken=" + jwtToken;
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(target)).build();
    }
}
