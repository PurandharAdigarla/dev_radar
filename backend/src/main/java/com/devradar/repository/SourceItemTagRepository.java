package com.devradar.repository;

import com.devradar.domain.SourceItemTag;
import com.devradar.domain.SourceItemTagId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SourceItemTagRepository extends JpaRepository<SourceItemTag, SourceItemTagId> {
    List<SourceItemTag> findBySourceItemId(Long sourceItemId);
    List<SourceItemTag> findBySourceItemIdIn(List<Long> sourceItemIds);
}
