package com.devradar.service.application;

import com.devradar.domain.exception.UserNotAuthenticatedException;
import com.devradar.security.SecurityUtils;
import com.devradar.service.UserInterestService;
import com.devradar.service.mapper.InterestTagMapper;
import com.devradar.web.rest.dto.InterestTagResponseDTO;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class InterestApplicationService {

    private final UserInterestService service;
    private final InterestTagMapper mapper;

    public InterestApplicationService(UserInterestService service, InterestTagMapper mapper) {
        this.service = service; this.mapper = mapper;
    }

    public List<InterestTagResponseDTO> myInterests() {
        Long uid = SecurityUtils.getCurrentUserId();
        if (uid == null) throw new UserNotAuthenticatedException();
        return service.findInterestsForUser(uid).stream().map(mapper::toDto).toList();
    }

    public List<InterestTagResponseDTO> setMyInterests(List<String> slugs) {
        Long uid = SecurityUtils.getCurrentUserId();
        if (uid == null) throw new UserNotAuthenticatedException();
        return service.setInterestsForUser(uid, slugs).stream().map(mapper::toDto).toList();
    }
}
