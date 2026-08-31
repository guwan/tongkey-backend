package com.tongkey.datasource;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DataSourceRepository extends JpaRepository<DataSourceConfig, String> {

    List<DataSourceConfig> findByEnabledTrue();
}
