package com.devradar.repository;

import com.devradar.domain.UserInterest;
import com.devradar.domain.UserInterestId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface UserInterestRepository extends JpaRepository<UserInterest, UserInterestId> {
    List<UserInterest> findByUserId(Long userId);

    @Modifying
    @Query("DELETE FROM UserInterest u WHERE u.userId = :userId")
    int deleteAllByUserId(Long userId);
}
