package com.devradar.repository;

import com.devradar.domain.SourceItemTag;
import com.devradar.domain.SourceItemTagId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SourceItemTagRepository extends JpaRepository<SourceItemTag, SourceItemTagId> {}
