package com.devradar.web.rest;

import com.devradar.service.application.InterestApplicationService;
import com.devradar.service.application.UserApplicationService;
import com.devradar.web.rest.dto.InterestTagResponseDTO;
import com.devradar.web.rest.dto.UserInterestsUpdateDTO;
import com.devradar.web.rest.dto.UserResponseDTO;
import com.devradar.web.rest.dto.UserUpdateDTO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserResource {

    private final UserApplicationService app;
    private final InterestApplicationService interests;

    public UserResource(UserApplicationService app, InterestApplicationService interests) {
        this.app = app;
        this.interests = interests;
    }

    @GetMapping("/me")
    public UserResponseDTO me() { return app.me(); }

    @PatchMapping("/me")
    public UserResponseDTO updateMe(@Valid @RequestBody UserUpdateDTO body) {
        return app.updateMe(body.displayName());
    }

    @GetMapping("/me/interests")
    public List<InterestTagResponseDTO> myInterests() { return interests.myInterests(); }

    @PutMapping("/me/interests")
    public List<InterestTagResponseDTO> setMyInterests(@Valid @RequestBody UserInterestsUpdateDTO body) {
        return interests.setMyInterests(body.tagSlugs());
    }
}
