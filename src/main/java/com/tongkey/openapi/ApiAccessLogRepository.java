package com.tongkey.openapi;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApiAccessLogRepository extends JpaRepository<ApiAccessLog, Long> {

    Page<ApiAccessLog> findByClientIdOrderByCreatedAtDesc(String clientId, Pageable pageable);

    Page<ApiAccessLog> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
