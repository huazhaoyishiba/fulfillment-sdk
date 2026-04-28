# fulfillment-sdk

一个基于 **Java 8 + Spring Boot 2.7.x** 的"支付后通用权益发放引擎"**多模块 SDK**。

**核心设计原则**：
- ❌ 不绑定数据库驱动（由接入方提供）
- ❌ 内核不绑定特定数据库（默认提供 5 种数据库建表脚本；默认 MyBatis Mapper 优先适配 MySQL/PostgreSQL/KingbaseES，Oracle/达梦可能需要自定义 Repository）
- ❌ 不绑定唯一 ORM（支持 MyBatis、JdbcTemplate、MyBatis-Plus、JPA 等）
- ✅ **starter 去 JDBC 化**：只引 starter，项目也能正常启动（不要求数据库）
- ✅ 核心层只定义 Repository 接口
- ✅ 持久化层可替换实现
- ✅ **多模块架构**：核心与持久化实现完全解耦
- ✅ **条件装配**：有 Repository 实现时自动装配完整服务，没有时只装配基础能力

**模块结构**：
- `fulfillment-core`：核心逻辑（实体、DTO、枚举、Service 接口、Handler SPI）
- `fulfillment-storage-spi`：持久化 SPI（Repository 接口定义）
- `fulfillment-storage-mybatis`：MyBatis 持久化实现（可选）
- `fulfillment-spring-boot-starter-boot2`：Spring Boot 2.x Starter（自动装配、配置、Service 实现，Java 8 兼容）

**数据库支持**：
- ✅ **MySQL** - 提供专用 SQL 脚本（`schema-mysql.sql`）
- ✅ **PostgreSQL** - 提供专用 SQL 脚本（`schema-postgresql.sql`）
- ✅ **Oracle** - 提供专用 SQL 脚本（`schema-oracle.sql`）
- ✅ **达梦数据库 (DM)** - 提供专用 SQL 脚本（`schema-dm.sql`）
- ✅ **人大金仓 (KingbaseES)** - 提供专用 SQL 脚本（`schema-kingbase.sql`）

> **注意**：SDK 只提供上述 5 种数据库的建表脚本。其他数据库（如 SQL Server、SQLite、H2、MariaDB、TiDB 等）需要根据兼容性选择对应脚本并自行调整。

## 定位
- 这不是支付网关 SDK。
- 这不是商城系统。
- 这不是文件系统。
- 它只负责：**支付成功后的幂等校验、发放任务创建、处理器调度、日志记录、失败重试**。

## 核心边界

### 默认持久化实现使用 4 张运行时核心表：
1. `sdk_payment_record` - 支付记录表
2. `sdk_idempotent_record` - 幂等记录表
3. `sdk_fulfillment_task` - 发放任务表
4. `sdk_fulfillment_log` - 发放日志表

> 内核本身不强制要求使用上述表结构，只定义 Repository 存储契约。默认 MyBatis 实现（`fulfillment-storage-mybatis`）以这 4 张表为基础；自行实现 Repository 时可使用任意存储方案。

### SDK **不接管**用户的：
- 用户表
- 订单主表
- 会员表 / 积分表 / 配额表 / 文件表

### SDK **不包含**：
- ❌ 数据库驱动（由接入方项目提供）
- ❌ 数据库连接配置（由接入方项目配置）
- ❌ 特定 ORM 框架（核心层只定义接口，实现可替换）
- ❌ **Web 层（无 Controller）**：SDK 只提供 `FulfillmentApplicationService`，你需要自己写 Controller
- ✅ 只提供表结构 SQL 脚本（支持多数据库）
- ✅ 提供 MyBatis 默认实现（可选，可替换）

## 企业级特性

✅ **完善的日志记录**：使用 SLF4J，覆盖关键流程和异常  
✅ **事务管理**：明确的事务边界，确保数据一致性  
✅ **幂等控制**：自动生成幂等键，支持并发安全  
✅ **异常处理**：完善的异常处理，防止敏感信息泄露  
✅ **错误截断**：自动截断超长错误信息，防止数据库字段溢出  
✅ **可配置策略**：支持配置重试次数、过期时间、超时时间等  
✅ **查询接口**：支持按订单号、任务号查询状态和详情  
✅ **Handler 缓存**：Handler 注册表使用缓存提高性能  
✅ **并发安全**：幂等记录插入支持并发场景  
✅ **超时控制**：Handler 执行支持超时控制，防止长时间阻塞  
✅ **错误码分类**：区分可重试和不可重试错误，智能判断重试策略  
✅ **补发机制**：支持手动补发，生成新任务，区别于重试

## 主流程
1. 外部系统确认支付成功，调用 SDK
2. SDK 检查幂等记录
3. 写入支付记录
4. 创建发放任务
5. 根据 `benefitTypeCode` 选择 Handler
6. 执行发放
7. 写发放日志
8. 更新任务状态并返回结果

## 核心 Service 接口

**重要**：SDK **不提供 Controller**，只提供 `FulfillmentApplicationService` 接口。你需要自己写 Controller 来暴露 HTTP 接口。

### 1. 确认支付并发放
```java
FulfillmentResponse confirmPaidAndFulfill(ConfirmPaymentRequest request);
```

### 2. 重试发放
```java
FulfillmentResponse retryByTaskNo(String taskNo);
```
**说明**：复用原任务，增加重试次数，保留原任务的所有日志。

### 3. 手动补发
```java
FulfillmentResponse manualFulfill(ManualFulfillRequest request);
```
**说明**：生成新任务，用于人工补偿场景。区别于重试，补发会创建全新的任务和日志。

### 4. 批量自动重试
```java
int retryFailedTasks();
```
**说明**：扫描所有可重试任务并自动重试。适合在定时任务中调用。

### 5. 查询发放状态
```java
FulfillmentStatusResponse queryByOrderNo(String bizOrderNo);
FulfillmentDetailResponse queryByTaskNo(String taskNo);
```

## Repository 接口（核心层）

SDK 核心层定义了 4 个 Repository 接口，不绑定具体实现：

- `PaymentRecordRepository` - 支付记录仓储
- `IdempotentRecordRepository` - 幂等记录仓储
- `FulfillmentTaskRepository` - 发放任务仓储
- `FulfillmentLogRepository` - 发放日志仓储

### 持久化实现（扩展层）

**方式一：使用 SDK 自带的 MyBatis 实现（推荐快速落地）**

需要**同时**引入 starter 和 MyBatis 存储模块：

```xml
<!-- 1. SDK Starter（必须） -->
<dependency>
    <groupId>com.example</groupId>
    <artifactId>fulfillment-spring-boot-starter-boot2</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</dependency>

<!-- 2. MyBatis 持久化实现（必须，提供 4 个 Repository 的 MyBatis 实现） -->
<dependency>
    <groupId>com.example</groupId>
    <artifactId>fulfillment-storage-mybatis</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</dependency>
```

> **依赖关系说明**：
> - `fulfillment-spring-boot-starter-boot2` 自动传递 `fulfillment-core` 和 `fulfillment-storage-spi`
> - `fulfillment-storage-mybatis` 自动传递 `mybatis-spring-boot-starter`（**无需单独引入**）
> - **Starter 不以 JDBC/MyBatis 为前提**，不引入 `fulfillment-storage-mybatis` 时项目也能正常启动
> - 使用 MyBatis 实现时，还需要添加**数据库驱动**和配置 `DataSource`

**方式二：自行实现 Repository（不依赖 MyBatis）**

如果项目使用 MyBatis-Plus、JdbcTemplate、JPA 或其他 ORM，只需引入 starter 并自行实现 4 个 Repository 接口：

```xml
<!-- 只需 Starter，不需要 fulfillment-storage-mybatis -->
<dependency>
    <groupId>com.example</groupId>
    <artifactId>fulfillment-spring-boot-starter-boot2</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</dependency>
```

```java
@Repository
public class MyBatisPlusPaymentRecordRepository implements PaymentRecordRepository {
    @Autowired
    private PaymentRecordMapper mapper; // MyBatis-Plus Mapper
    // 实现接口方法...
}
```

> **注意**：只要注册了自定义 Repository Bean，SDK 的 MyBatis 实现会自动让位（`@ConditionalOnMissingBean`）。

## 快速开始

### 1. 添加依赖

在你的项目中添加 SDK 依赖（假设已发布到 Maven 仓库）：

```xml
<!-- 1. SDK Starter（必须） -->
<dependency>
    <groupId>com.example</groupId>
    <artifactId>fulfillment-spring-boot-starter-boot2</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</dependency>

<!-- 2. MyBatis 持久化实现（推荐，提供默认存储） -->
<dependency>
    <groupId>com.example</groupId>
    <artifactId>fulfillment-storage-mybatis</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</dependency>
```

> **依赖关系说明**：
> - `fulfillment-spring-boot-starter-boot2` 自动传递：
>   - `fulfillment-core`（核心逻辑）
>   - `fulfillment-storage-spi`（持久化接口）
>   - `spring-boot-starter-validation`（参数校验，JSR-303）
> - **Starter 不以 JDBC/MyBatis 为前提**，只引入 starter 项目也能正常启动（不要求数据库）
> - `fulfillment-storage-mybatis` 自动传递 `mybatis-spring-boot-starter`，无需单独引入

### 2. 选择持久化实现

**选项 A：使用 SDK 自带的 MyBatis 实现（推荐）**

引入 `fulfillment-storage-mybatis` 后，SDK 会自动检测并注册 4 个 Repository Bean（MyBatis 实现）。无需自行实现 Repository，无需额外持久化代码。

> **前提条件**：确保项目已配置 `DataSource`，且 MyBatis 的 `SqlSessionFactory` 可用。

**选项 B：自行实现 Repository（不依赖 MyBatis）**

如果项目使用 MyBatis-Plus、JdbcTemplate、JPA 或其他 ORM，不需要引入 `fulfillment-storage-mybatis`，只需自行实现 4 个 Repository 接口并注册为 Spring Bean。SDK 会自动检测到 Repository Bean 存在并装配完整服务。

### 3. 添加数据库驱动（使用持久化时必须）

**重要**：无论选择哪种持久化实现，SDK 都不包含数据库驱动，需要根据你使用的数据库自行添加：

**MySQL:**
```xml
<dependency>
    <groupId>com.mysql</groupId>
    <artifactId>mysql-connector-j</artifactId>
</dependency>
```

**PostgreSQL:**
```xml
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
</dependency>
```

**Oracle:**
```xml
<dependency>
    <groupId>com.oracle.database.jdbc</groupId>
    <artifactId>ojdbc8</artifactId>
</dependency>
```

**国产数据库示例（达梦数据库）：**
```xml
<dependency>
    <groupId>com.dameng</groupId>
    <artifactId>DmJdbcDriver</artifactId>
    <version>8.1.2.192</version>
</dependency>
```

**国产数据库示例（人大金仓）：**
```xml
<dependency>
    <groupId>com.kingbase</groupId>
    <artifactId>kingbase8</artifactId>
    <version>8.6.0</version>
</dependency>
```

### 4. 配置数据库连接

在你的 `application.yml` 中配置数据库连接（SDK 会使用你项目已有的数据源）：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/your_db
    username: your_user
    password: your_password
    driver-class-name: com.mysql.cj.jdbc.Driver
```

### 5. 执行建表 SQL

SDK 提供了 5 种数据库的建表脚本，位于 `fulfillment-storage-mybatis/src/main/resources/` 目录：

- `schema-mysql.sql` - MySQL
- `schema-postgresql.sql` - PostgreSQL
- `schema-oracle.sql` - Oracle
- `schema-dm.sql` - 达梦数据库 (DM)
- `schema-kingbase.sql` - 人大金仓 (KingbaseES)

> **注意**：SDK 只提供上述 5 种数据库的建表脚本。其他数据库需要根据兼容性选择对应脚本并自行调整。

### 6. 实现 Handler

```java
@Component
public class MembershipHandler implements FulfillmentHandler {
    @Override
    public boolean supports(String benefitTypeCode) {
        return "MEMBERSHIP".equals(benefitTypeCode);
    }

    @Override
    public HandlerResult fulfill(FulfillmentTask task) {
        // 解析配置并执行会员开通逻辑
        String config = task.getBenefitConfigSnapshot();
        // ... 你的业务逻辑
        return HandlerResult.success("会员开通30天");
    }
}
```

### 7. 调用 SDK

```java
@RestController
public class PaymentController {
    @Autowired
    private FulfillmentApplicationService fulfillmentService;

    @PostMapping("/api/payment/confirm")
    public ResponseEntity<String> confirmPayment(@RequestBody ConfirmPaymentRequest request) {
        request.setBenefitTypeCode("MEMBERSHIP");  // 必填：权益类型编码
        request.setBenefitConfigSnapshot("{\"days\":30}");  // 必填：权益配置快照
        
        FulfillmentResponse result = fulfillmentService.confirmPaidAndFulfill(request);
        
        if (result.isSuccess()) {
            return ResponseEntity.ok("发放成功: " + result.getTaskNo());
        } else {
            return ResponseEntity.ok("发放失败: " + result.getErrorCode());
        }
    }
}
```

**完成！** 此时支付回调进来后，SDK 会自动完成幂等 → 建任务 → 调度你的 Handler → 记录日志 → 返回结果。

## 数据库支持

### ✅ 已提供 SQL 脚本（5 种数据库）

SDK 提供了以下 5 种数据库的建表脚本，位于 `fulfillment-storage-mybatis/src/main/resources/` 目录：

| 数据库 | SQL 脚本 | 驱动依赖 | 说明 |
|--------|---------|---------|------|
| **MySQL** | `schema-mysql.sql` | `com.mysql:mysql-connector-j` | 推荐，默认实现已测试 |
| **PostgreSQL** | `schema-postgresql.sql` | `org.postgresql:postgresql` | 推荐，默认实现已测试 |
| **Oracle** | `schema-oracle.sql` | `com.oracle.database.jdbc:ojdbc8` | 使用序列和触发器 |
| **达梦数据库 (DM)** | `schema-dm.sql` | `dm.jdbc.driver.DmDriver` | 兼容 Oracle 语法 |
| **人大金仓 (KingbaseES)** | `schema-kingbase.sql` | `com.kingbase8.Driver` | 兼容 PostgreSQL 语法 |

> **注意**：SDK 只提供上述 5 种数据库的建表脚本。其他数据库（如 SQL Server、SQLite、H2、MariaDB、TiDB 等）需要根据兼容性选择对应脚本并自行调整。

### 📝 注意事项

1. **MyBatis Mapper 中的 SQL**：
   - 当前 Mapper 使用了 `limit` 与 `now()`（MySQL/PostgreSQL/KingbaseES/H2 友好）
   - **Oracle/达梦数据库**：需要自定义 Repository 实现，将 `limit 1` 改为 `ROWNUM = 1` 或 `FETCH FIRST 1 ROW ONLY`
   - **人大金仓**：兼容 PostgreSQL 语法，可直接使用 `limit` 语法

2. **其他数据库**：
   - 如果使用其他数据库（如 MariaDB、TiDB、SQL Server 等），需要根据兼容性选择对应脚本并自行调整
   - 建议参考兼容的数据库脚本（如 MariaDB 使用 MySQL 脚本，TiDB 使用 MySQL 脚本）

## 配置说明

SDK 支持以下配置（`application.yml`）：

```yaml
fulfillment:
  # 默认最大重试次数
  default-max-retry-count: 3
  # 幂等记录过期时间（小时）
  idempotent-expire-hours: 24
  # Handler 执行超时时间（秒），0 表示不设置超时
  handler-timeout-seconds: 30
  # 超时控制线程池配置
  handler-thread-pool-core-size: 2
  handler-thread-pool-max-size: 10
  handler-thread-pool-keep-alive-seconds: 60
  handler-thread-pool-queue-capacity: 200
  # 自动重试（宿主应用自行调度 retryFailedTasks()）
  retry-batch-size: 50            # 每次调用 retryFailedTasks() 最多处理的任务数
```

## 自动重试调度

SDK 提供了 `retryFailedTasks()` 方法用于批量重试失败任务。**SDK 不内置调度器**，由宿主应用自行决定如何调度。
建议同时调度 `recoverStuckTasks()`，用于把超时卡在 `PROCESSING` 状态的任务回收为 `RETRY_WAIT`。

```java
// 方式 1：Spring @Scheduled（单实例部署）
@Scheduled(fixedDelay = 60000)
public void retryFulfillment() {
    fulfillmentService.retryFailedTasks();
}

// 方式 2：外部分布式调度平台（多实例部署，推荐）
// 如 XXL-JOB / Elastic-Job / Quartz Cluster / K8s CronJob
// 调度入口：fulfillmentService.retryFailedTasks()
```

> **设计原则**：调度策略因项目而异（单实例 vs 分布式、调度频率、监控需求等），SDK 只暴露方法，不替你做决定。

## 参数校验

SDK 使用 JSR-303 进行参数校验，`fulfillment-spring-boot-starter-boot2` 已包含 `spring-boot-starter-validation` 依赖。

如果项目中没有其他模块引入 `spring-boot-starter-validation`，SDK 会自动提供。如果项目中已有该依赖，则使用项目中的版本。

## 常见问题

### Q1: 启动报 `PaymentRecordRepository that could not be found`

**原因**：没有可用的 Repository 实现。Starter 本身不包含持久化实现，需要额外引入。

**解决**：
- **方式 A**：引入 `fulfillment-storage-mybatis` 依赖（它会自动传递 `mybatis-spring-boot-starter`），并配置好 DataSource
- **方式 B**：自行实现 4 个 Repository 接口并注册为 Spring Bean（适合 MyBatis-Plus / JPA / JdbcTemplate 项目）

> **注意**：如果只引入 starter 不引入任何 Repository 实现，SDK 只装配基础能力（Properties、HandlerRegistry），**不会报错**，但也不会注册 `FulfillmentApplicationService`。

### Q2: 同一个支付回调被多次调用会怎样？

**答**：SDK 内置幂等机制。第一次调用正常执行，后续相同幂等键的调用直接返回第一次的结果，不会重复执行 Handler。

返回的 `FulfillmentResponse.message` 为 `"idempotent replay"`。

### Q3: Handler 执行超时会怎样？

**答**：SDK 会对执行线程发送中断信号，任务标记为 `RETRY_WAIT`（可重试），错误码为 `HANDLER_TIMEOUT`。超时时间通过 `fulfillment.handler-timeout-seconds` 配置。

> 注意：中断是协作式机制。若你的 Handler 使用不可中断阻塞调用，或未检查中断状态，超时后业务逻辑仍可能继续运行。建议在 Handler 中显式处理 `InterruptedException`，并在循环/长耗时逻辑中检查 `Thread.currentThread().isInterrupted()`。

### Q4: 如何自定义权益类型？

**答**：`benefitTypeCode` 使用字符串类型，支持任意自定义值。只需在 Handler 的 `supports()` 方法中匹配对应的编码即可。

```java
@Override
public boolean supports(String benefitTypeCode) {
    return "COUPON".equals(benefitTypeCode) || "POINTS".equals(benefitTypeCode);
}
```

### Q5: benefitConfigSnapshot 为什么是快照？

**答**：因为用户的权益配置可能随时变化（比如今天会员 30 天，明天改成 15 天）。快照保证：
- 重试时使用的是**下单时**的配置，而不是当前最新配置
- 审计时能追溯当时的发放条件

## 许可证

MIT License

## 开源发布说明

本项目以开源 SDK 形式发布，目标是提供通用的支付后权益履约能力（幂等、任务编排、重试、日志追溯），而非完整业务系统。

- 支持范围：当前面向 Java 8 + Spring Boot 2.7.x，默认提供可替换的存储实现与多数据库建表脚本。
- 兼容声明：不同数据库与 ORM 场景下可能需要接入方做适配（例如 SQL 方言、Repository 自定义实现）。
- 稳定性说明：发布版本遵循语义化版本管理（`MAJOR.MINOR.PATCH`），`SNAPSHOT` 仅用于开发阶段，不建议生产环境直接依赖。
- 安全与合规：仓库不应包含任何真实密钥、口令或私有凭据；如发现潜在安全问题，请通过 Issue 反馈并避免公开敏感细节。
- 免责说明：本项目按 MIT 许可证 “as is” 提供，不对接入方的业务损失、数据风险或合规责任承担担保义务。
