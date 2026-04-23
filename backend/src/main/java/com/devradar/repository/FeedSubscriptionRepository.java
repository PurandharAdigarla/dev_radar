package com.devradar.repository;

import com.devradar.domain.FeedSubscription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FeedSubscriptionRepository extends JpaRepository<FeedSubscription, Long> {
    List<FeedSubscription> findByActiveTrue();
}
