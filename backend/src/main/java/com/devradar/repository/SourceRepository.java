package com.devradar.repository;

import com.devradar.domain.Source;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface SourceRepository extends JpaRepository<Source, Long> {
    Optional<Source> findByCode(String code);
}
