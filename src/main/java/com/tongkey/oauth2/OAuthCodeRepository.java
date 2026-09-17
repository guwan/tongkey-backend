package com.tongkey.oauth2;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface OAuthCodeRepository extends JpaRepository<OAuthCodeEntity, String> {

    Optional<OAuthCodeEntity> findByCodeHash(String codeHash);

    /** 原子核销：仅当未使用时置 used=true，返回受影响行数（0 表示已被并发使用=重放）。 */
    @Modifying
    @Query("update OAuthCodeEntity c set c.used = true where c.id = :id and c.used = false")
    int markUsed(@Param("id") String id);

    /** 清理已使用或已过期的授权码（可由维护任务/启动时调用）。 */
    @Modifying
    @Query("delete from OAuthCodeEntity c where c.used = true or c.expiresAt < :now")
    int deleteExpiredOrUsed(@Param("now") Instant now);
}
