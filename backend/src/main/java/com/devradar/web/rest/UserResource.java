package com.devradar.web.rest;

import com.devradar.domain.User;
import com.devradar.repository.UserRepository;
import com.devradar.service.application.InterestApplicationService;
import com.devradar.service.application.UserApplicationService;
import com.devradar.web.rest.dto.InterestTagResponseDTO;
import com.devradar.web.rest.dto.UserInterestsUpdateDTO;
import com.devradar.web.rest.dto.UserResponseDTO;
import com.devradar.web.rest.dto.UserUpdateDTO;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserResource {

    private final UserApplicationService app;
    private final InterestApplicationService interests;
    private final UserRepository userRepo;

    public UserResource(UserApplicationService app, InterestApplicationService interests,
                        UserRepository userRepo) {
        this.app = app;
        this.interests = interests;
        this.userRepo = userRepo;
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

    /** Lookup a user by email. Returns their id and displayName. */
    @GetMapping("/search")
    public UserSearchResult searchByEmail(@RequestParam String email) {
        User user = userRepo.findByEmail(email)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No user with that email"));
        return new UserSearchResult(user.getId(), user.getDisplayName(), user.getEmail());
    }

    public record UserSearchResult(Long id, String displayName, String email) {}
}
