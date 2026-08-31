package com.tongkey;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * TongKey 开放式授权中心。
 *
 * <p>核心子系统：核心域模型（用户/角色/权限）、第三方 SQL 数据源拉取、
 * 主动数据推送（Webhook）、开放 REST API、API 文档、审计日志。</p>
 */
@SpringBootApplication
@EnableScheduling
@EnableAsync
public class TongKeyApplication {

    public static void main(String[] args) {
        SpringApplication.run(TongKeyApplication.class, args);
    }
}
