# TongKey 开放式授权中心系统规格文档

版本：v0.2（草案）
日期：2026-08-28

---

## 1. 项目背景与目标

### 1.1 背景
当前企业内部存在多套业务系统，各自维护用户、角色、权限数据，缺乏统一的授权管理中枢。部分系统的用户/角色/权限数据源自第三方系统（如 HIS、OA 等），需要定期或按需同步；同时本系统也需要将维护好的用户/角色/权限数据对外提供，供其他系统查询和调用。

### 1.2 目标
构建一个**开放式授权中心**，具备以下核心能力：

1. **数据来源多样化**：用户、角色、权限数据既可以由本系统直接创建维护（原生数据），也可以通过**配置化的 SQL 数据源**从第三方数据库拉取并落地到本系统。
2. **对外提供数据服务**：提供 REST API，供第三方系统查询、创建、更新本系统的用户/角色/权限数据（需要认证鉴权）。
3. **主动数据推送**：支持配置化的"推送目标"，在系统初始化或数据发生变更时，主动调用第三方提供的接口，将数据变更推送出去。
4. **易于对接的接口文档**：提供在线 API 文档（OpenAPI/Swagger 风格），并配套清晰的示例、错误码说明、变更历史，便于第三方（包括 AI 辅助编码）快速理解并编写对接代码。

### 1.3 非目标（本期不做）
- 不做完整的单点登录（SSO）协议实现（如 OAuth2/OIDC 全套、CAS），仅做用户/角色/权限的**数据**中心，鉴权协议对接可作为后续迭代。
- 不做复杂的工作流审批（权限申请审批流可作为二期）。

---

## 2. 总体架构

```
                         ┌─────────────────────────────┐
                         │        前端管理控制台         │
                         │  Vite + React + Tailwind     │
                         └───────────────┬──────────────┘
                                          │ HTTPS / REST
                         ┌───────────────▼──────────────┐
                         │         授权中心后端           │
                         │   Spring Boot + Spring JPA    │
                         │                                │
   ┌─────────────┐       │  ┌──────────┐  ┌────────────┐ │       ┌──────────────┐
   │ 第三方数据库  │◄──────┤  │ 数据源采集 │  │  核心域模型  │ │──────►│  第三方系统A  │
   │(见5.1.1支持列表)│ SQL │  │  调度引擎  │  │用户/角色/权限│ │ 推送  │(HIS/OA等)    │
   └─────────────┘       │  └──────────┘  └────────────┘ │       └──────────────┘
                         │  ┌──────────┐  ┌────────────┐ │
   ┌─────────────┐       │  │开放REST  │  │  推送引擎    │ │       ┌──────────────┐
   │ 第三方系统B  │◄──────┼──┤  API     │  │ (Webhook)  │ ├──────►│  第三方系统B  │
   │ (调用查询接口)│──────►│  └──────────┘  └────────────┘ │       └──────────────┘
   └─────────────┘       │  ┌────────────────────────┐   │
                         │  │  API 文档站点 (OpenAPI)  │   │
                         │  └────────────────────────┘   │
                         └────────────────────────────────┘
                                          │
                                 ┌────────▼────────┐
                                 │  自身数据库(RDBMS) │
                                 └──────────────────┘
```

### 2.2 核心子系统划分

| 子系统 | 职责 |
|---|---|
| **核心域模型** | 用户、角色、权限、用户-角色、角色-权限、数据来源标记（原生/同步） |
| **数据源接入（拉取）** | 配置第三方数据库连接、SQL 语句、字段映射、调度周期，执行拉取并做增量/全量同步入库 |
| **数据推送（主动推送）** | 配置第三方 HTTP 接口（Webhook）、触发时机（初始化/新增/修改/删除）、推送数据格式、重试策略 |
| **开放 REST API** | 供第三方查询/创建/更新用户、角色、权限数据，含鉴权与限流 |
| **API 文档站点** | OpenAPI/Swagger 自动生成 + 定制化文档页面（含示例代码、变更日志） |
| **管理控制台（前端）** | 数据源配置、推送配置、用户角色权限管理、API Key 管理、同步/推送日志查看 |
| **审计与日志** | 记录所有数据变更来源（谁改的、通过什么渠道改的）、同步/推送执行记录 |

---

## 3. 技术栈

### 3.1 前端
- **构建工具**：Vite
- **包管理**：pnpm
- **框架**：React 18+（函数组件 + Hooks）
- **样式**：Tailwind CSS
- **建议补充**：
  - 路由：React Router
  - 数据请求/缓存：TanStack Query (React Query)
  - 表单：React Hook Form + Zod（校验，尤其是 SQL 数据源配置表单）
  - 表格/树形组件：用于角色-权限树、用户列表分页
  - 代码编辑器组件（如 Monaco Editor 或 CodeMirror）：用于在页面上编写/调试第三方 SQL 语句

### 3.2 后端
- **语言**：Java 25（当前 LTS，2025 年 9 月发布；新项目建议直接以此为基线，可用虚拟线程、结构化并发、Compact Object Headers 等特性降低同步/推送场景下的并发开销与内存占用）
- **框架**：Spring Boot 4.1.x（基于 Spring Framework 7、Jakarta EE 11；注意包名已全面为 `jakarta.*`，如从旧项目迁移需完成 `javax.*→jakarta.*` 替换）
- **持久化**：Spring Data JPA + Hibernate 7.x
- **数据库**：本系统自身建议使用 MySQL/PostgreSQL（存储核心域数据、配置元数据）
- **第三方数据源连接**：动态数据源（`DataSource` 按配置动态创建，通过 JDBC 驱动 + `JdbcTemplate` 执行配置化 SQL，而非通过 JPA 实体，因为源端结构不可控；虚拟线程可用于并发拉取多个数据源而不必担心线程池耗尽）。**第一期明确支持的第三方数据库类型**见 5.1 节，后续类型按需扩展
- **任务调度**：Spring Task Scheduler 或 Quartz（用于定时拉取、定时推送重试）
- **API 文档**：springdoc-openapi（OpenAPI 3 + Swagger UI，需使用兼容 Spring Boot 4 / Spring Framework 7 的版本）
- **鉴权**：Spring Security 7.1 + API Key / JWT（详见第 6 章）
- **消息/异步**（可选二期）：若推送量大，可引入消息队列（RabbitMQ/RocketMQ）做削峰和重试；结合虚拟线程后，简单的异步推送也可不依赖 MQ 而直接用轻量级线程池支撑较大并发

> 版本策略建议：CI 中同步跑一份 Java 26（最新非 LTS）做兼容性探测，但生产环境固定在 Java 25 LTS + Spring Boot 4.1 的稳定组合上，跟随 Spring Boot 的 12 个月 OSS 支持周期升级小版本。

---

## 4. 核心领域模型

### 4.1 实体设计

#### User（用户）
| 字段 | 说明 |
|---|---|
| id | 主键（本系统内部 ID，UUID 或雪花 ID） |
| username | 登录名/唯一标识 |
| display_name | 显示名称 |
| status | 状态（启用/禁用） |
| source_type | 数据来源类型：`NATIVE`（本系统创建）/ `SYNCED`（第三方同步） |
| source_id | 若为同步数据，记录来源数据源配置 ID |
| external_key | 第三方系统中的原始主键/唯一标识（用于同步时做 upsert 匹配） |
| extra_attrs | JSON 扩展字段，存放第三方特有的用户属性 |
| created_at / updated_at | 时间戳 |
| created_by / updated_by | 操作者（系统内部账号 或 "SYNC:数据源名" 或 "API:调用方标识"） |

#### Role（角色）
| 字段 | 说明 |
|---|---|
| id, code, name, description | 基本信息，`code` 唯一 |
| source_type / source_id / external_key | 同 User，标记数据来源 |
| extra_attrs | 扩展属性 |

#### Permission（权限）
| 字段 | 说明 |
|---|---|
| id, code, name, description | 基本信息 |
| resource_type | 权限所属资源类型（菜单/接口/按钮/数据权限等，可扩展枚举） |
| source_type / source_id / external_key | 同上 |

#### UserRole / RolePermission（关联表）
标准多对多关联表，同样带 `source_type`/`source_id` 以区分该关联关系是原生维护还是同步而来。

#### 关键设计原则
- **来源可追溯**：所有数据都要能区分"本系统原生录入"还是"来自某个第三方同步源"，避免同步覆盖用户手工维护的数据造成冲突。
- **冲突策略可配置**：当某条记录既被本系统手工修改过，又有新的同步数据到达时，需要配置策略（如：`SYNC_OVERRIDE`覆盖 / `SYNC_SKIP_IF_MODIFIED`保留本地修改 / `MERGE_FIELD_LEVEL`按字段合并）。

---

## 5. 数据拉取：第三方 SQL 数据源配置

### 5.1 数据源配置模型（DataSourceConfig）
| 字段 | 说明 |
|---|---|
| id, name | 数据源名称 |
| db_type | 数据库类型（Oracle/MySQL/SQL Server...） |
| jdbc_url / username / password（加密存储） | 连接信息 |
| enabled | 是否启用 |
| schedule_cron | 定时拉取的 cron 表达式（可留空，表示仅手动触发） |
| sync_mode | `FULL`（全量覆盖）/ `INCREMENTAL`（增量，依据时间戳/自增ID字段） |
| incremental_column | 增量同步依据的字段（如 `update_time`） |

#### 5.1.1 支持的第三方数据库类型（第一期）

`db_type` 为枚举值，第一期明确支持以下五种，后续按接入需求再扩展（如 DB2、达梦、人大金仓等国产库）：

| db_type 枚举值 | 数据库 | JDBC 驱动（Maven 坐标） | JDBC URL 示例 | 备注 |
|---|---|---|---|---|
| `MYSQL` | MySQL（含兼容协议的云数据库） | `com.mysql:mysql-connector-j` | `jdbc:mysql://host:3306/db?useSSL=false&serverTimezone=Asia/Shanghai` | 建议同时兼容 8.0 与较新的次版本 |
| `MARIADB` | MariaDB | `org.mariadb.jdbc:mariadb-java-client` | `jdbc:mariadb://host:3306/db` | 驱动与 MySQL 不通用，需单独配置；虽兼容 MySQL 协议，但部分方言（如分页、函数）有差异，需在 SQL 校验/方言层区分 |
| `POSTGRESQL` | PostgreSQL | `org.postgresql:postgresql` | `jdbc:postgresql://host:5432/db` | 支持 schema 概念，配置时需允许指定 `search_path` 或在 SQL 中显式带 schema 前缀 |
| `ORACLE` | Oracle | `com.oracle.database.jdbc:ojdbc11` | `jdbc:oracle:thin:@host:1521/service_name` 或 `@host:1521:SID` | 需重点支持：①**dblink 跨库引用**（如 `table@dblink_name`，源 SQL 中可能出现，需保证驱动/连接池不做过度解析改写）；②服务名（Service Name）与 SID 两种连接方式都要支持；③ Navicat 等工具习惯用字面量而非绑定变量，本系统执行层需统一走绑定变量以防注入，同时在 SQL 编辑预览时给出实际拼参后的语句方便核对 |
| `SQLSERVER` | SQL Server | `com.microsoft.sqlserver:mssql-jdbc` | `jdbc:sqlserver://host:1433;databaseName=db;encrypt=true;trustServerCertificate=true` | 注意 Windows 身份验证 vs SQL Server 身份验证两种模式；`encrypt`/`trustServerCertificate` 参数在较新驱动下默认值有变化，需显式配置避免连接失败 |

**实现建议：**
- 后端通过一个 `DbDialectStrategy`（或类似的策略接口）按 `db_type` 封装差异点：分页语法（`LIMIT` vs `ROWNUM`/`OFFSET FETCH` vs `TOP`）、只读校验规则、字段类型映射（如 Oracle 的 `NUMBER`、SQL Server 的 `NVARCHAR`）等，避免核心同步逻辑里散落 `if/else` 判断数据库类型。
- 每种 `db_type` 对应的驱动作为**可选依赖**按需引入（尤其 Oracle 的 `ojdbc` 因许可协议通常需要单独从 Oracle Maven 仓库或本地仓库获取，不在 Maven Central 直接可用），避免所有驱动打包进主应用膨胀体积。
- 连接测试功能（管理台"测试连接"按钮）需针对每种 `db_type` 做最小化探测查询（如 `SELECT 1`、Oracle 用 `SELECT 1 FROM DUAL`）。
- 只读校验（5.4 节提到的 SQL 前置校验）需要考虑各库方言差异，例如 Oracle/SQL Server 允许 `WITH ... AS` CTE 开头的只读查询也应放行，不能简单地只认 `SELECT` 开头。

### 5.2 SQL 映射配置（SyncMapping）
每个数据源下可配置多个映射任务，分别对应"拉取用户"、"拉取角色"、"拉取权限"、"拉取用户角色关系"等：

| 字段 | 说明 |
|---|---|
| id, data_source_id | 关联数据源 |
| target_entity | 目标实体：`USER` / `ROLE` / `PERMISSION` / `USER_ROLE` / `ROLE_PERMISSION` |
| sql_text | 配置的查询 SQL（支持占位符，如 `:lastSyncTime` 用于增量同步） |
| field_mapping | JSON，描述 SQL 结果列 → 目标实体字段的映射，例如：
```json
{
  "external_key": "USER_ID",
  "username": "LOGIN_NAME",
  "display_name": "USER_NAME",
  "status": "IS_ACTIVE"
}
```
| conflict_strategy | 冲突处理策略（见 4.1 节末尾） |
| batch_size | 每批处理条数（防止大结果集一次性载入内存） |

### 5.3 执行流程
1. 调度触发（定时或手动"立即同步"按钮）。
2. 动态创建/复用 JDBC 连接，执行配置的 SQL（增量模式下自动拼接时间过滤条件）。
3. 按 `field_mapping` 将结果集逐行映射为目标实体的 DTO。
4. 按 `external_key` 做 upsert：已存在则按冲突策略更新，不存在则新增。
5. 记录本次同步日志（成功/失败条数、错误详情、耗时）。
6. 若配置了推送规则（第 6 章），同步完成后触发对应的推送事件。

### 5.4 安全与稳定性考虑
- 第三方数据库连接密码需加密存储（如 Jasypt 或自研 AES 加密），页面展示时脱敏。
- 执行 SQL 前建议做**只读校验**（限制以 `SELECT` 开头，避免误配置破坏源库）。
- 拉取需支持超时控制、失败重试（有限次数）、失败告警通知。
- 大数据量拉取需分页/流式处理，避免 OOM。

---

## 6. 数据推送：主动推送给第三方

### 6.1 推送目标配置（PushTarget）
| 字段 | 说明 |
|---|---|
| id, name | 推送目标名称 |
| endpoint_url | 第三方接收接口地址 |
| http_method | POST/PUT 等 |
| auth_type | 鉴权方式（无/Basic/Bearer Token/自定义 Header 签名） |
| auth_config | 鉴权所需的密钥等信息（加密存储） |
| trigger_events | 触发时机：`ON_INIT`（系统初始化/全量推送一次）、`ON_CREATE`、`ON_UPDATE`、`ON_DELETE` |
| entity_scope | 推送哪些实体：User/Role/Permission/关联关系，可多选 |
| payload_template | 推送报文的字段映射/模板（本系统字段 → 第三方期望字段名） |
| retry_policy | 重试次数、间隔（如指数退避） |
| enabled | 启用/禁用 |

### 6.2 触发机制
- **初始化推送**：在推送目标被启用（或手动点击"全量推送一次"）时，将当前全部符合 `entity_scope` 的数据一次性（分批）推送过去。
- **增量推送**：核心域数据发生变更（无论是通过管理台手工修改、开放 API 修改，还是第三方 SQL 同步导入）时，通过**领域事件（Spring `ApplicationEvent`）**发布变更事件，推送引擎监听事件并异步执行推送，避免阻塞主流程。
- **失败处理**：推送失败进入重试队列，超过最大重试次数后标记为失败并记录，支持在管理台手动重推。

### 6.3 建议技术实现
- 使用 Spring 事件机制（`@TransactionalEventListener`，在事务提交后触发）确保推送的数据已经落库成功。
- 推送任务异步执行（`@Async` 或专用线程池），并落"推送日志表"（PushLog）记录每次推送的请求体、响应状态码、响应内容、耗时。
- 若未来推送量大或需要严格顺序保证，可替换为 MQ 方案。

---

## 7. 开放 REST API（供第三方查询/更新）

### 7.1 API 分类
| 分类 | 示例接口 | 说明 |
|---|---|---|
| 用户查询 | `GET /api/v1/users`、`GET /api/v1/users/{id}` | 支持分页、按 `source_type`/`status`/关键字过滤 |
| 用户写入 | `POST /api/v1/users`、`PUT /api/v1/users/{id}` | 第三方可创建/更新用户（写入的数据自动标记为 `source_type=API`，并记录调用方） |
| 角色查询/写入 | `GET/POST/PUT /api/v1/roles` | 同上 |
| 权限查询/写入 | `GET/POST/PUT /api/v1/permissions` | 同上 |
| 关联关系 | `GET/POST/DELETE /api/v1/users/{id}/roles`、`/api/v1/roles/{id}/permissions` | 用户-角色、角色-权限绑定/解绑 |
| 批量接口 | `POST /api/v1/users/batch` | 批量创建/更新，供初始化场景使用 |
| 变更查询（供第三方主动拉取） | `GET /api/v1/changes?since={timestamp}&entity=USER` | 面向不希望被动接收推送、而是想自己定时拉增量的第三方 |

### 7.2 通用规范
- 统一响应结构：`{ code, message, data, traceId }`
- 统一分页参数：`page`、`size`、`sort`
- 统一错误码体系（区分参数错误、鉴权失败、资源不存在、冲突等）
- 幂等性：写接口建议支持 `external_key` + 幂等 Header，避免第三方重复提交造成脏数据
- 版本化：路径前缀 `/api/v1/`，为未来变更预留空间

### 7.3 鉴权与权限控制（"授权中心"自身的开放接口也需要被授权）
- 采用 **API Key / Client 凭证机制**：为每个接入方（第三方系统）分配 `client_id` + `client_secret` 或长期 API Key。
- 支持基于 Client 的**接口级权限控制**：每个第三方只能访问被授权的实体和操作（如 A 系统只读、B 系统可写用户但不能写角色）。
- 建议同时支持签名校验（如 HMAC-SHA256 对请求体+时间戳签名），防止重放攻击。
- 所有开放 API 调用记录访问日志（调用方、接口、参数摘要、响应状态、耗时），便于审计和排查。
- 限流：按 Client 维度做速率限制（如令牌桶），防止单一第三方压垮系统。

---

## 8. API 文档站点

### 8.1 基础能力
- 基于 **springdoc-openapi** 自动生成 OpenAPI 3.0 规范文档，暴露 `Swagger UI` 页面供交互式调试。
- 每个接口需完善以下元数据（供人类和 AI 阅读都友好）：
  - 接口用途描述、请求/响应字段说明及示例、错误码列表、鉴权方式说明。
  - 请求/响应的 JSON Schema 明确到字段级（类型、是否必填、枚举值范围）。

### 8.2 增强的文档页面（前端自建，非纯 Swagger UI）
考虑到目标读者包含"对方的 AI"，建议额外提供一个结构化程度更高的文档页面：
- **快速开始（Quick Start）**：从"申请 API Key"到"发起第一次请求"的完整示例（含 curl/Java/Python 代码片段）。
- **数据模型字典**：所有实体字段的中英文说明、类型、示例值，机器可读（可导出 JSON）。
- **变更日志（Changelog）**：接口版本变更记录，便于第三方感知破坏性变更。
- **OpenAPI JSON/YAML 下载入口**：方便第三方或 AI 直接导入到 Postman/代码生成工具，自动生成 SDK。
- **Webhook 接收端规范说明**：若第三方希望接收本系统的主动推送，文档需清晰说明推送报文格式、签名校验方式、期望的响应格式（用于确认接收成功），并提供示例的接收端实现（伪代码）。

---

## 9. 管理控制台（前端）功能规划

| 模块 | 功能 |
|---|---|
| 用户/角色/权限管理 | 增删改查、批量导入导出、来源标记展示（原生/同步/API写入）、变更历史查看 |
| 数据源管理 | 新增/编辑第三方数据库连接、SQL 映射配置（含在线 SQL 调试执行预览）、手动触发同步、查看同步日志 |
| 推送目标管理 | 新增/编辑推送配置、手动触发全量推送、查看推送日志与失败重试 |
| 开放 API 管理 | Client/API Key 管理、接口权限分配、调用日志与限流配置查看 |
| 审计日志 | 全局操作审计（谁、何时、通过什么渠道、改了什么） |
| 仪表盘 | 数据总量、近期同步/推送成功率、异常告警展示 |

---

## 10. 非功能性需求

| 维度 | 要求 |
|---|---|
| 安全 | 敏感配置（数据库密码、推送密钥）加密存储；开放 API 全量鉴权+限流+审计；SQL 拉取限制只读 |
| 可扩展性 | 数据源类型、推送鉴权方式、字段映射均需配置化，避免新增第三方时改代码 |
| 可观测性 | 同步任务、推送任务、开放 API 调用均需完整日志与失败告警（可对接邮件/企业微信/钉钉机器人） |
| 数据一致性 | 明确的冲突处理策略；关键写操作需事务保证；推送需在数据落库事务提交后触发 |
| 性能 | 大批量同步需支持分页/流式处理；开放 API 查询需支持索引优化的分页 |
| 部署 | 建议容器化部署（Docker），配置通过环境变量/配置中心管理，便于多环境（测试/生产）隔离 |

---

## 11. 建议的迭代路线

**第一期（MVP）**
- 核心域模型（User/Role/Permission 及关联）+ 管理控制台基础 CRUD
- 单一第三方 SQL 数据源接入（全量同步）
- 开放 REST API（查询 + 基础写入）+ API Key 鉴权
- OpenAPI/Swagger 文档自动生成

**第二期**
- 增量同步 + 冲突处理策略
- 主动推送引擎（初始化推送 + 增量事件推送）+ 推送日志与重试
- 增强文档站点（Quick Start、数据字典、Changelog）

**第三期**
- 多数据源多映射的批量管理、SQL 在线调试器
- 更细粒度的开放 API 权限控制（按 Client 控制字段级读写）
- 审计与监控告警体系完善、限流与安全加固（签名校验、防重放）

---

## 12. 待确认问题（建议下一步与相关方确认）

1. 本系统自身需不需要对接现有的登录鉴权体系（如是否要接入企业已有 SSO），还是管理控制台自建一套简单的登录即可？
2. ~~第三方数据库以 Oracle 为主还是需要同时支持多种类型？~~ **已确认**：第一期支持 MySQL、Oracle、PostgreSQL、MariaDB、SQL Server 五种（详见 5.1.1 节），后续视接入需求再扩展其他类型。Oracle 场景需重点考虑 dblink/多库架构（如同 HIS 系统）。
3. 数据量级预估（用户/角色/权限的规模），决定是否需要引入 MQ 等异步组件。
4. 推送失败时，是否需要人工介入的告警渠道（邮件/钉钉/企业微信)？
5. 是否需要支持多租户（即本授权中心同时服务多个独立的业务系统，数据相互隔离）？
