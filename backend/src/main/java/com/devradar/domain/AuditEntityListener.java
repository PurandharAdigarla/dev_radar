package com.devradar.domain;

import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import java.time.Instant;

public class AuditEntityListener {

    // TODO(T8): once SecurityUtils exists, populate createdBy/updatedBy from the current JWT principal.
    @PrePersist
    public void onCreate(Object entity) {
        if (entity instanceof BaseAuditableEntity e) {
            e.createdAt = Instant.now();
            e.createdBy = null;
        }
    }

    @PreUpdate
    public void onUpdate(Object entity) {
        if (entity instanceof BaseAuditableEntity e) {
            e.updatedAt = Instant.now();
            e.updatedBy = null;
        }
    }
}
