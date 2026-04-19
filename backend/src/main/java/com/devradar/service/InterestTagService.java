package com.devradar.service;

import com.devradar.domain.InterestCategory;
import com.devradar.domain.InterestTag;
import com.devradar.repository.InterestTagRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
public class InterestTagService {
    private final InterestTagRepository repo;

    public InterestTagService(InterestTagRepository repo) {
        this.repo = repo;
    }

    public Page<InterestTag> search(String q, InterestCategory category, Pageable pageable) {
        String trimmed = (q == null || q.isBlank()) ? null : q.trim();
        return repo.search(trimmed, category, pageable);
    }
}
