package com.tongkey.sync;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SyncLogRepository extends JpaRepository<SyncLog, Long> {

    Page<SyncLog> findByMappingIdOrderByStartedAtDesc(String mappingId, Pageable pageable);

    Page<SyncLog> findByDataSourceIdOrderByStartedAtDesc(String dataSourceId, Pageable pageable);

    Page<SyncLog> findAllByOrderByStartedAtDesc(Pageable pageable);

    long countByStatusAndStartedAtAfter(SyncLog.SyncStatus status, java.time.Instant since);
}
