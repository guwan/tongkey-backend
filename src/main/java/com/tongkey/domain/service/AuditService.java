package com.tongkey.domain.service;

import com.tongkey.domain.ChangeAction;
import com.tongkey.domain.EntityType;
import com.tongkey.domain.entity.AuditLog;
import com.tongkey.domain.repository.AuditLogRepository;
import org.springframework.stereotype.Service;

/**
 * 审计服务：所有写路径统一经此记录"谁、何时、通过什么渠道、改了什么"。
 */
@Service
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    public void record(String channel, String operator, EntityType entityType, String entityId,
                       String entityCode, ChangeAction action, String detail) {
        AuditLog log = new AuditLog();
        log.setChannel(channel);
        log.setOperator(operator);
        log.setEntityType(entityType);
        log.setEntityId(entityId);
        log.setEntityCode(entityCode);
        log.setAction(action);
        log.setDetail(detail);
        auditLogRepository.save(log);
    }
}
