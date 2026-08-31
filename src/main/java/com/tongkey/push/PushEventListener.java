package com.tongkey.push;

import com.tongkey.domain.event.EntityChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 推送事件监听（规格文档 6.2 / 6.3）：
 * 监听核心域变更事件，且仅在事务提交后（AFTER_COMMIT）触发，
 * 确保推送的数据已经落库成功；推送本身异步执行，不阻塞主流程。
 */
@Component
public class PushEventListener {

    private static final Logger log = LoggerFactory.getLogger(PushEventListener.class);

    private final PushEngine pushEngine;

    public PushEventListener(PushEngine pushEngine) {
        this.pushEngine = pushEngine;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onEntityChanged(EntityChangedEvent event) {
        try {
            pushEngine.onChange(event.entityType(), event.action(), event.entityId(), event.snapshot());
        } catch (Exception e) {
            log.error("推送分发异常: {}", e.getMessage(), e);
        }
    }
}
