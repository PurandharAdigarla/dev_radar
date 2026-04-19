package com.devradar.repository;

import com.devradar.domain.SourceItem;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface SourceItemRepository extends JpaRepository<SourceItem, Long> {
    Optional<SourceItem> findBySourceIdAndExternalId(Long sourceId, String externalId);
    boolean existsBySourceIdAndExternalId(Long sourceId, String externalId);
}
