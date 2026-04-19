package com.devradar.service.application;

import com.devradar.domain.exception.UserNotAuthenticatedException;
import com.devradar.security.SecurityUtils;
import com.devradar.service.UserService;
import com.devradar.service.mapper.UserMapper;
import com.devradar.web.rest.dto.UserResponseDTO;
import org.springframework.stereotype.Service;

@Service
public class UserApplicationService {
    private final UserService users;
    private final UserMapper mapper;

    public UserApplicationService(UserService users, UserMapper mapper) {
        this.users = users;
        this.mapper = mapper;
    }

    public UserResponseDTO me() {
        Long id = SecurityUtils.getCurrentUserId();
        if (id == null) throw new UserNotAuthenticatedException();
        return mapper.toDto(users.findById(id));
    }

    public UserResponseDTO updateMe(String displayName) {
        Long id = SecurityUtils.getCurrentUserId();
        if (id == null) throw new UserNotAuthenticatedException();
        return mapper.toDto(users.updateDisplayName(id, displayName));
    }
}
