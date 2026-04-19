package com.devradar.web.rest;

import com.devradar.service.application.UserApplicationService;
import com.devradar.web.rest.dto.UserResponseDTO;
import com.devradar.web.rest.dto.UserUpdateDTO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
public class UserResource {
    private final UserApplicationService app;

    public UserResource(UserApplicationService app) { this.app = app; }

    @GetMapping("/me")
    public UserResponseDTO me() { return app.me(); }

    @PatchMapping("/me")
    public UserResponseDTO updateMe(@Valid @RequestBody UserUpdateDTO body) {
        return app.updateMe(body.displayName());
    }
}
