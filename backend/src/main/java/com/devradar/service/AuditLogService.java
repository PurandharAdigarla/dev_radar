package com.devradar.service;

import com.devradar.domain.AuditLog;
import com.devradar.repository.AuditLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditLogService {
    private final AuditLogRepository repo;
    public AuditLogService(AuditLogRepository repo) { this.repo = repo; }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(Long userId, String action, String entity, String entityId, String detailsJson) {
        AuditLog a = new AuditLog();
        a.setUserId(userId);
        a.setAction(action);
        a.setEntity(entity);
        a.setEntityId(entityId);
        a.setDetails(detailsJson);
        repo.save(a);
    }
}
