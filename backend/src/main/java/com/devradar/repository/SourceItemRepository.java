package com.devradar.repository;

import com.devradar.domain.SourceItem;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SourceItemRepository extends JpaRepository<SourceItem, Long> {
    Optional<SourceItem> findBySourceIdAndExternalId(Long sourceId, String externalId);
    boolean existsBySourceIdAndExternalId(Long sourceId, String externalId);

    @Query("""
        SELECT DISTINCT si FROM SourceItem si, SourceItemTag sit, InterestTag it, UserInterest ui
         WHERE sit.sourceItemId = si.id
           AND it.id = sit.interestTagId
           AND ui.interestTagId = it.id
           AND ui.userId = :userId
           AND si.postedAt >= :since
           AND (:tagSlug IS NULL OR it.slug = :tagSlug)
         ORDER BY si.postedAt DESC
        """)
    List<SourceItem> findRecentByUserInterestsPaged(
        @Param("userId") Long userId,
        @Param("since") Instant since,
        @Param("tagSlug") String tagSlug,
        Pageable pageable);

    default List<SourceItem> findRecentByUserInterests(Long userId, Instant since, String tagSlug, int limit) {
        return findRecentByUserInterestsPaged(userId, since, tagSlug, PageRequest.of(0, limit));
    }
}
