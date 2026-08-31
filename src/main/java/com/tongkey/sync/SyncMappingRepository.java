package com.tongkey.sync;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SyncMappingRepository extends JpaRepository<SyncMapping, String> {

    List<SyncMapping> findByDataSourceId(String dataSourceId);

    List<SyncMapping> findByDataSourceIdAndEnabledTrue(String dataSourceId);
}
