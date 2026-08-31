# TongKey Backend

TongKey 开放式授权中心后端服务。用户 / 角色 / 权限数据中心：管理控制台后端 + 第三方数据源同步 + Webhook 推送引擎 + 开放 REST API。

## 技术栈

| 组件 | 技术 |
|---|---|
| 语言 | Java 24（Gradle toolchain 自动下载） |
| 框架 | Spring Boot 4.1.1 / Spring Data JPA / Spring Security |
| 数据库 | PostgreSQL 17（开发环境 Hibernate 自动建表） |
| API 文档 | springdoc-openapi 3.1.0 / Swagger UI |
| 并发模型 | Spring 虚拟线程 |
| 构建工具 | Gradle 8.14（Kotlin DSL，自带 Wrapper） |
| 第三方 JDBC | MySQL / MariaDB / Oracle / SQL Server |

## 核心功能

- 🔐 **管理控制台 API**：用户、角色、权限、审计日志、开放 API 接入方管理
- 🔄 **第三方数据源同步**：定时从 MySQL / PostgreSQL / Oracle / SQL Server / MariaDB 同步用户数据
- 📤 **Webhook 推送引擎**：支持 NONE / BASIC / BEARER / HMAC-SIGNATURE 四种鉴权方式，事件触发自动推送
- 🌐 **开放 REST API**：X-API-Key 鉴权 + HMAC-SHA256 可选签名校验，多租户接入方管理，接口级 scope 权限控制
- 📊 **审计与安全**：统一 traceId、操作审计日志、敏感字段 AES 加密存储

## 目录结构

```
tongkey-backend/
├── src/main/java/com/tongkey/
│   ├── TongKeyApplication.java          # Spring Boot 启动类
│   ├── console/                          # 管理控制台控制器
│   ├── domain/                           # 领域层（Entity / Repository / Service）
│   ├── datasource/                       # 第三方数据源连接管理
│   ├── sync/                             # 同步引擎
│   ├── push/                             # 推送引擎
│   ├── openapi/                          # 开放 API（Client 管理 + 鉴权 + 限流）
│   ├── security/                         # Spring Security + 双过滤器
│   └── common/                           # 通用工具（响应 / 异常 / 加密 / TraceId）
├── src/main/resources/
│   ├── application.yml                   # 通用基础配置（不包含敏感值）
│   ├── application-dev.yml               # 开发环境默认值（已提交）
│   ├── application-prod.yml              # 生产环境配置（已提交，敏感值通过环境变量）
│   └── application-local.yml             # 本地私有覆盖（gitignore，每个开发者自己放）
├── build.gradle.kts                      # Gradle Kotlin DSL 构建文件
├── settings.gradle.kts                   # Gradle 设置 + 仓库
├── gradlew / gradlew.bat                 # Gradle Wrapper 启动脚本
├── gradle/wrapper/gradle-wrapper.jar    # Wrapper bootstrap jar
└── gradle/wrapper/gradle-wrapper.properties
```

## 多环境配置

项目使用 Spring Profiles 管理多环境配置，分层叠加生效：

```
application.yml（通用基础）
  + application-dev.yml（开发环境，默认激活）
  + application-local.yml（本地私有覆盖，不提交）
```

| 文件 | 提交到 git？ | 用途 |
|---|---|---|
| `application.yml` | ✅ | 各环境共享的通用配置（框架、JPA、虚拟线程、Swagger 等） |
| `application-dev.yml` | ✅ | 开发环境合理默认值：localhost PostgreSQL、自动建表、DEBUG 日志 |
| `application-prod.yml` | ✅ | 生产安全配置：生产数据库必填、ddl-auto=validate、INFO 日志 |
| `application-local.yml` | ❌ **gitignore** | 每个开发者本地私有覆盖，如自定义 DB、端口、密码 |

### 切换环境

```bash
# 开发（默认，dev + local 叠加）
./gradlew bootRun

# 仅 dev
SPRING_PROFILES_ACTIVE=dev ./gradlew bootRun

# 生产（敏感字段必须通过环境变量注入）
SPRING_PROFILES_ACTIVE=prod ./gradlew bootRun

# dev 基础 + local 覆盖（推荐本地开发方式）
SPRING_PROFILES_ACTIVE=dev,local ./gradlew bootRun
```

## 快速开始

### 环境要求

- **JDK 24** — Gradle toolchain 会自动下载所需 JDK，也可手动设置 `JAVA_HOME`
- **PostgreSQL 17+**

### 1. 创建数据库

```sql
CREATE DATABASE tongkey;
```

### 2. 可选：创建本地私有配置

在 `src/main/resources/` 下创建 `application-local.yml`（此文件被 gitignore，可放心写入敏感值）：

```yaml
# 示例：改 DB 端口或连接远程开发库
spring:
  datasource:
    url: jdbc:postgresql://192.168.1.100:5432/tongkey_dev

# 示例：改端口避免冲突
server:
  port: 8081

# 示例：自定义 admin 密码
tongkey:
  admin:
    password: my-local-admin-pass
```

### 3. 构建与运行（Gradle Wrapper）

```bash
# Windows PowerShell
.\gradlew.bat bootRun                     # 开发运行（默认 dev + local 叠加）
.\gradlew.bat compileJava                # 仅编译
.\gradlew.bat bootJar                    # 打包可执行 jar
.\gradlew.bat test                       # 运行测试

# macOS / Linux
./gradlew bootRun
./gradlew bootJar
```

Gradle Wrapper 会自动下载 Gradle 8.14（无需全局安装）。首次运行 Gradle toolchain 会自动下载 JDK 24。

#### 通过代理下载（国内网络）

如果通过代理上网，需要设置 `GRADLE_OPTS` 让 Gradle daemon 走代理：

```powershell
$env:GRADLE_OPTS = "-Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort=7890 -Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=7890"
.\gradlew.bat bootRun
```

### 4. 验证

| 端点 | 地址 |
|---|---|
| 健康检查 | `GET http://localhost:8080/actuator/health` |
| Swagger UI | `http://localhost:8080/swagger-ui/index.html` |
| OpenAPI 文档 | `GET http://localhost:8080/v3/api-docs` |
| 管理控制台登录 | `POST http://localhost:8080/console/auth/login` |
| 开放 API | `http://localhost:8080/api/v1/**`（需 X-API-Key） |

### 5. 环境变量（覆盖配置文件）

以下关键值通过环境变量注入，优先级高于配置文件：

| 变量 | 说明 | 生产是否必须 |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | 激活的 profile，如 `prod` | ✅ |
| `TONGKEY_DB_URL` | 数据库连接 URL | ✅（prod 必填） |
| `TONGKEY_DB_USER` | 数据库用户名 | ✅（prod 必填） |
| `TONGKEY_DB_PASSWORD` | 数据库密码 | ✅（prod 必填） |
| `TONGKEY_CRYPTO_KEY` | 敏感字段 AES 加密密钥 | ✅（prod 必填） |
| `TONGKEY_ADMIN_PASSWORD` | 控制台管理员密码 | ✅（prod 必填） |

## 常用 Gradle 命令

| 命令 | 说明 |
|---|---|
| `./gradlew bootRun` | 开发运行 |
| `./gradlew compileJava` | 仅编译源码 |
| `./gradlew bootJar` | 打包可执行 jar 到 `build/libs/tongkey-server.jar` |
| `./gradlew bootWar` | 打包 WAR（需额外配置） |
| `./gradlew clean` | 清理 `build/` |
| `./gradlew build` | clean + compile + jar |
| `./gradlew test` | 运行测试 |
| `./gradlew dependencies` | 查看依赖树 |

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
