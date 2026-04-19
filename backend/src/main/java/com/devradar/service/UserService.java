package com.devradar.service;

import com.devradar.domain.User;
import com.devradar.domain.exception.UserNotFoundException;
import com.devradar.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {
    private final UserRepository repo;

    public UserService(UserRepository repo) { this.repo = repo; }

    public User findById(Long id) {
        return repo.findById(id).orElseThrow(() -> new UserNotFoundException(id));
    }

    @Transactional
    public User updateDisplayName(Long id, String displayName) {
        User u = findById(id);
        u.setDisplayName(displayName);
        return u;
    }
}
