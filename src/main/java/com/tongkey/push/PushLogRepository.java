package com.tongkey.push;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface PushLogRepository extends JpaRepository<PushLog, Long> {

    Page<PushLog> findByTargetIdOrderByCreatedAtDesc(String targetId, Pageable pageable);

    Page<PushLog> findAllByOrderByCreatedAtDesc(Pageable pageable);

    List<PushLog> findByStatusAndNextRetryAtLessThanEqual(PushLog.PushStatus status, Instant now);

    long countByStatusAndCreatedAtAfter(PushLog.PushStatus status, Instant since);
}
