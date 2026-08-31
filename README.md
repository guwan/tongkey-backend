# TongKey Backend

TongKey 开放式授权中心后端服务。用户 / 角色 / 权限数据中心：管理控制台后端 + 第三方数据源同步 + Webhook 推送引擎 + 开放 REST API。

## 技术栈

| 组件 | 技术 |
|---|---|
| 语言 | Java 24 |
| 框架 | Spring Boot 4.1.1 / Spring Data JPA / Spring Security |
| 数据库 | PostgreSQL 17（Hibernate 自动建表） |
| API 文档 | springdoc-openapi 3.1.0 / Swagger UI |
| 并发模型 | Spring 虚拟线程 |
| 构建工具 | Maven |
| 第三方 JDBC | MySQL / MariaDB / Oracle / SQL Server |

## 核心功能

- 🔐 **管理控制台 API**：用户、角色、权限、审计日志、开放 API 接入方管理
- 🔄 **第三方数据源同步**：定时从 MySQL / PostgreSQL / Oracle / SQL Server / MariaDB 同步用户数据
- 📤 **Webhook 推送引擎**：支持 NONE / BASIC / BEARER / HMAC-SIGNATURE 四种鉴权方式，事件触发自动推送
- 🌐 **开放 REST API**：X-API-Key 鉴权 + HMAC-SHA256 可选签名校验，多租户接入方管理，接口级 scope 权限控制
- 📊 **审计与安全**：统一 traceId、操作审计日志、敏感字段 AES 加密存储

## 目录结构

```
src/main/java/com/tongkey/
├── TongKeyApplication.java          # Spring Boot 启动类
├── console/                          # 管理控制台控制器（Auth / User / Role / Permission / Dashboard / Audit）
├── domain/                           # 领域层（Entity / Repository / Service）
├── sync/                             # 同步引擎（多数据源 JDBC 读取 + 定时调度）
├── push/                             # 推送引擎（Webhook 目标管理 + 事件触发推送）
├── openapi/                          # 开放 API（Client 管理 + API 鉴权过滤器 + 限流）
├── security/                         # Spring Security 配置 + 双过滤器
├── common/                           # 通用工具（统一响应 / 异常 / 加密 / 分页 / TraceId）
└── datasource/                       # 第三方数据源连接管理
```

## 快速开始

### 环境要求

- JDK 24（pom 已锁定 `<java.version>24</java.version>`）
- PostgreSQL 17+

### 1. 创建数据库

```sql
CREATE DATABASE tongkey;
```

### 2. 配置环境变量（可选，覆盖默认值）

| 变量 | 默认值 | 说明 |
|---|---|---|
| `TONGKEY_DB_URL` | `jdbc:postgresql://localhost:5432/tongkey` | 数据库连接 |
| `TONGKEY_DB_USER` | `postgres` | 数据库用户 |
| `TONGKEY_DB_PASSWORD` | `postgres` | 数据库密码 |
| `TONGKEY_CRYPTO_KEY` | `TongKey-Dev-AES-Key-2026-08-28!` | 敏感字段加密密钥（**生产必须覆盖**） |
| `TONGKEY_ADMIN_USER` | `admin` | 控制台管理员用户名 |
| `TONGKEY_ADMIN_PASSWORD` | `Admin@123` | 控制台管理员密码（**生产必须覆盖**） |

### 3. 构建与运行

```bash
# 开发运行
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-24'  # Windows PowerShell
mvn spring-boot:run

# 打包
mvn clean package -DskipTests

# 运行打包后的 jar
java -jar target/tongkey-server.jar
```

### 4. 验证

| 端点 | 地址 |
|---|---|
| 健康检查 | `GET http://localhost:8080/actuator/health` |
| Swagger UI | `http://localhost:8080/swagger-ui/index.html` |
| OpenAPI 文档 | `GET http://localhost:8080/v3/api-docs` |
| 管理控制台登录 | `POST http://localhost:8080/console/auth/login` |
| 开放 API | `http://localhost:8080/api/v1/**`（需 X-API-Key） |

## 关键约定

- **统一响应格式**：`{ "code": 0, "message": "成功", "data": {...}, "traceId": "..." }`，code=0 表示成功
- **控制台鉴权**：`POST /console/auth/login` 获取 Bearer Token，后续请求通过 `Authorization: Bearer <token>` 携带
- **开放 API 鉴权**：`X-API-Key` 必填；可选开启签名校验（`X-Timestamp` + `X-Signature` = HMAC-SHA256(clientSecret, method + "\n" + path + "\n" + timestamp + "\n" + body)）
- **写接口幂等**：支持 `externalKey` 字段，重复提交相同值转为更新操作
- **分页**：`page`（0 起）+ `size`，返回 `{ items, total, page, size }`

## 架构说明

详细规格文档见 [TongKey-系统规格文档.md](./TongKey-系统规格文档.md)。

### 双过滤器安全设计

- `ConsoleAuthFilter`：处理 `/console/**` 路径的 Bearer Token 鉴权，使用内置 `AdminTokenService` 颁发/验证
- `OpenApiAuthFilter`：处理 `/api/v1/**` 路径的 X-API-Key + HMAC-SHA256 签名校验，带令牌桶限流

两者均由 `SecurityConfig` 显式声明为 `@Bean` 并用 `FilterRegistrationBean.setEnabled(false)` 禁止 Spring 全局注册，避免绕过安全链拦截无关路径。

### 模块边界

```
┌─────────────┐     ┌──────────────┐     ┌─────────────┐
│  Console API │────▶│  Domain Layer│◀────│  Open API   │
└─────────────┘     └──────┬───────┘     └─────────────┘
                            │
                    ┌───────┴───────┐
                    │  Persistence  │
                    │  (JPA / PG)   │
                    └───────────────┘
```

## 许可证

[MIT License](./LICENSE)
