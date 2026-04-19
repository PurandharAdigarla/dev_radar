package com.devradar.domain;

import jakarta.persistence.*;
import java.time.Instant;

@MappedSuperclass
@EntityListeners(AuditEntityListener.class)
public abstract class BaseAuditableEntity {
    @Column(name = "created_at", nullable = false, updatable = false)
    protected Instant createdAt;
    @Column(name = "created_by", updatable = false)
    protected Long createdBy;
    @Column(name = "updated_at")
    protected Instant updatedAt;
    @Column(name = "updated_by")
    protected Long updatedBy;

    public Instant getCreatedAt() { return createdAt; }
    public Long getCreatedBy() { return createdBy; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Long getUpdatedBy() { return updatedBy; }
}
