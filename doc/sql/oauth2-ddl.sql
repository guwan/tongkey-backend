-- ============================================================
--  TongKey OAuth2 模块 DDL（PostgreSQL）
--  生产环境 spring.jpa.hibernate.ddl-auto=validate，部署前执行：
--    psql -h <host> -U <user> -d tongkey -f oauth2-ddl.sql
--  开发环境 ddl-auto=update 会自动建表建列，无需执行本脚本。
--  脚本可重复执行（IF NOT EXISTS）。
-- ============================================================

-- 1. 接入方表增加 OAuth2 重定向 URI 白名单（一行一个或逗号分隔）
ALTER TABLE tk_client
    ADD COLUMN IF NOT EXISTS redirect_uris text;

-- 2. OAuth2 授权码（一次性、短有效期，仅存哈希）
CREATE TABLE IF NOT EXISTS tk_oauth_code (
    id           varchar(36)  NOT NULL,
    code_hash    varchar(64)  NOT NULL,
    client_id    varchar(64)  NOT NULL,
    user_id      varchar(36)  NOT NULL,
    username     varchar(128) NOT NULL,
    scopes       varchar(1024) NOT NULL,
    redirect_uri varchar(1024) NOT NULL,
    expires_at   timestamptz  NOT NULL,
    used         boolean      NOT NULL DEFAULT false,
    created_at   timestamptz  NOT NULL,
    CONSTRAINT pk_oauth_code PRIMARY KEY (id)
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_oauth_code_hash
    ON tk_oauth_code (code_hash);
CREATE INDEX IF NOT EXISTS idx_oauth_code_client
    ON tk_oauth_code (client_id, expires_at);

-- 3. OAuth2 刷新令牌（仅存哈希，使用即旋转）
CREATE TABLE IF NOT EXISTS tk_oauth_refresh_token (
    id         varchar(36)  NOT NULL,
    token_hash varchar(64)  NOT NULL,
    client_id  varchar(64)  NOT NULL,
    user_id    varchar(36)  NOT NULL,
    username   varchar(128) NOT NULL,
    scopes     varchar(1024) NOT NULL,
    expires_at timestamptz  NOT NULL,
    revoked    boolean      NOT NULL DEFAULT false,
    created_at timestamptz  NOT NULL,
    CONSTRAINT pk_oauth_refresh_token PRIMARY KEY (id)
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_oauth_refresh_hash
    ON tk_oauth_refresh_token (token_hash);
CREATE INDEX IF NOT EXISTS idx_oauth_refresh_client
    ON tk_oauth_refresh_token (client_id, expires_at);
