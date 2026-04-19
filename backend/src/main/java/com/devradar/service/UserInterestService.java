package com.devradar.service;

import com.devradar.domain.InterestTag;
import com.devradar.domain.UserInterest;
import com.devradar.domain.exception.InterestTagNotFoundException;
import com.devradar.repository.InterestTagRepository;
import com.devradar.repository.UserInterestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class UserInterestService {

    private final UserInterestRepository userInterestRepo;
    private final InterestTagRepository tagRepo;

    public UserInterestService(UserInterestRepository userInterestRepo, InterestTagRepository tagRepo) {
        this.userInterestRepo = userInterestRepo; this.tagRepo = tagRepo;
    }

    public List<InterestTag> findInterestsForUser(Long userId) {
        var interests = userInterestRepo.findByUserId(userId);
        if (interests.isEmpty()) return List.of();
        return tagRepo.findAllById(interests.stream().map(UserInterest::getInterestTagId).toList());
    }

    @Transactional
    public List<InterestTag> setInterestsForUser(Long userId, List<String> slugs) {
        Set<String> distinct = new HashSet<>(slugs);
        List<InterestTag> tags = tagRepo.findBySlugIn(List.copyOf(distinct));
        if (tags.size() != distinct.size()) {
            Set<String> found = new HashSet<>();
            for (InterestTag t : tags) found.add(t.getSlug());
            for (String s : distinct) if (!found.contains(s)) throw new InterestTagNotFoundException(s);
        }
        userInterestRepo.deleteAllByUserId(userId);
        for (InterestTag t : tags) userInterestRepo.save(new UserInterest(userId, t.getId()));
        return tags;
    }
}
