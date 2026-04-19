package com.devradar.repository;

import com.devradar.domain.InterestCategory;
import com.devradar.domain.InterestTag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface InterestTagRepository extends JpaRepository<InterestTag, Long> {
    Optional<InterestTag> findBySlug(String slug);
    List<InterestTag> findBySlugIn(List<String> slugs);

    @Query("SELECT t FROM InterestTag t WHERE " +
           "(:q IS NULL OR LOWER(t.displayName) LIKE LOWER(CONCAT('%', :q, '%')) OR LOWER(t.slug) LIKE LOWER(CONCAT('%', :q, '%'))) " +
           "AND (:category IS NULL OR t.category = :category)")
    Page<InterestTag> search(@Param("q") String q, @Param("category") InterestCategory category, Pageable pageable);
}
