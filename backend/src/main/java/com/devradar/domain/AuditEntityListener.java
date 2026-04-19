package com.devradar.domain;

import com.devradar.security.SecurityUtils;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import java.time.Instant;

public class AuditEntityListener {

    @PrePersist
    public void onCreate(Object entity) {
        if (entity instanceof BaseAuditableEntity e) {
            e.createdAt = Instant.now();
            e.createdBy = SecurityUtils.getCurrentUserId();
        }
    }

    @PreUpdate
    public void onUpdate(Object entity) {
        if (entity instanceof BaseAuditableEntity e) {
            e.updatedAt = Instant.now();
            e.updatedBy = SecurityUtils.getCurrentUserId();
        }
    }
}
