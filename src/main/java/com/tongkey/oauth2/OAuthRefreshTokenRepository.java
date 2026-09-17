package com.tongkey.oauth2;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface OAuthRefreshTokenRepository extends JpaRepository<OAuthRefreshTokenEntity, String> {

    Optional<OAuthRefreshTokenEntity> findByTokenHash(String tokenHash);

    /** 原子旋转：仅当未撤销时置 revoked=true，返回受影响行数（0 表示已被并发使用=重放）。 */
    @Modifying
    @Query("update OAuthRefreshTokenEntity t set t.revoked = true where t.id = :id and t.revoked = false")
    int markRevoked(@Param("id") String id);

    @Modifying
    @Query("delete from OAuthRefreshTokenEntity t where t.revoked = true or t.expiresAt < :now")
    int deleteRevokedOrExpired(@Param("now") Instant now);
}
