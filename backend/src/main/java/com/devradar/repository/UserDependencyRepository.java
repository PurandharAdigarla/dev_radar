package com.devradar.repository;

import com.devradar.domain.UserDependency;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserDependencyRepository extends JpaRepository<UserDependency, Long> {

    Optional<UserDependency> findByUserIdAndRepoFullNameAndFilePathAndPackageName(
        Long userId, String repoFullName, String filePath, String packageName);

    List<UserDependency> findByUserId(Long userId);

    @Query("SELECT DISTINCT d.ecosystem FROM UserDependency d WHERE d.userId = :userId")
    List<String> findDistinctEcosystemsByUserId(@Param("userId") Long userId);

    @Query("SELECT DISTINCT d.ecosystem, d.packageName, d.currentVersion FROM UserDependency d")
    List<Object[]> findDistinctPackages();
}
