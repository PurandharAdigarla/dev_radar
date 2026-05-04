package com.devradar.web.rest;

import com.devradar.service.AuthService;
import com.devradar.web.rest.dto.*;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthResource {

    private final AuthService auth;

    public AuthResource(AuthService auth) {
        this.auth = auth;
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
}
