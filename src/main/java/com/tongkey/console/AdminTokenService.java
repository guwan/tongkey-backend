package com.tongkey.console;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 管理控制台会话令牌（内置账号登录）。
 * <p>规格文档 12.1：本期采用简单登录，后续可替换为企业 SSO。</p>
 */
@Service
public class AdminTokenService {

    private final String username;
    private final String password;
    private final long ttlMinutes;
    private final Map<String, Instant> tokens = new ConcurrentHashMap<>();

    public AdminTokenService(@Value("${tongkey.admin.username}") String username,
                             @Value("${tongkey.admin.password}") String password,
                             @Value("${tongkey.admin.token-ttl-minutes:480}") long ttlMinutes) {
        this.username = username;
        this.password = password;
        this.ttlMinutes = ttlMinutes;
    }

    public String login(String user, String pwd) {
        if (username.equals(user) && password.equals(pwd)) {
            String token = UUID.randomUUID().toString().replace("-", "");
            tokens.put(token, Instant.now().plusSeconds(ttlMinutes * 60));
            return token;
        }
        return null;
    }

    public boolean isValid(String token) {
        if (token == null) {
            return false;
        }
        Instant expire = tokens.get(token);
        if (expire == null) {
            return false;
        }
        if (Instant.now().isAfter(expire)) {
            tokens.remove(token);
            return false;
        }
        return true;
    }

    public void logout(String token) {
        if (token != null) {
            tokens.remove(token);
        }
    }
}
