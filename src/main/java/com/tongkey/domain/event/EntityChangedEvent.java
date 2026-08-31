package com.tongkey.domain.event;

import com.tongkey.domain.ChangeAction;
import com.tongkey.domain.EntityType;

import java.time.Instant;
import java.util.Map;

/**
 * 核心域数据变更事件（规格文档 6.2）。
 * <p>由写路径在事务内发布，推送引擎通过 {@code @TransactionalEventListener(AFTER_COMMIT)}
 * 在事务提交后异步消费，保证推送的数据已经落库成功。</p>
 */
public record EntityChangedEvent(
        EntityType entityType,
        ChangeAction action,
        String entityId,
        /** 业务标识：用户名/角色编码/权限编码，或关联关系复合键 */
        String entityCode,
        /** 变更后的实体快照（删除时为空），用于渲染推送报文 */
        Map<String, Object> snapshot,
        /** 变更渠道，如 CONSOLE / API:xxx / SYNC:xxx */
        String channel,
        Instant occurredAt) {
}
