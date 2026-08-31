package com.tongkey.push;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PushTargetRepository extends JpaRepository<PushTarget, String> {

    List<PushTarget> findByEnabledTrue();
}
