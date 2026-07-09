# titanium-policy 保单域 - 多 Agent 协作指南

> 适用范围：仅 `titanium-policy` 微服务的跨 Agent 协作
> 上级指南：根目录 [AGENTS.md](../AGENTS.md)
> 模块开发规约见同目录 [CLAUDE.md](./CLAUDE.md)
> 本文档基于模块实际代码探查

---

## 一、模块定位与边界

保单域是承保主链路的执行中枢，**上游接收**意向/核保/缴费信号，**下游产出**正式保单状态。三个聚合根
（Proposal → Insurance → Policy）构成单向推进的业务流，跨聚合协作通过 `IssuanceSaga` 编排，禁止聚合根之间直接调用。

```
        ┌─────────── titanium-policy（本模块，端口 8080）────────────┐
上游 →  │  Proposal ──> Insurance ──[IssuanceSaga]──> Policy        │ → 下游
        │     ↑核保结果        ↑产品/条款/规则配置                    │
        └──────────────────────────────────────────────────────────┘
```

---

## 二、与其他域的交互点

### 2.1 本模块对外暴露（被调用）

- **Feign 接口**：`com.titanium.policy.api.PolicyApi`（`@FeignClient(name = "titanium-policy")`，路由 `/api/policies`）。
  由 `PolicyController` 实现（`@RequestMapping("/web/policies")`）。提供建单、一步出单、智能出单、签发、激活、暂停、恢复、终止、取消、按客户/状态查询等端点。
- **Kafka 事件**（`PolicyConstants.KafkaTopic`，仅这 2 个事件实际外发）：
  - `titanium.policy.created`（POLICY_CREATED） ← `PolicyCreatedEvent`
  - `titanium.policy.activated`（POLICY_ACTIVATED） ← `PolicyActivatedEvent`
  - 已声明未使用的 topic：`titanium.policy.expired`、`titanium.policy.cancelled`、`titanium.policy.events`。

### 2.2 本模块依赖（主动调用，application/service 层）

| 目标域 | Feign 客户端类型 | 本地封装 Service | 防腐端口（domain/service） | Adapter |
|--------|----------------|-----------------|---------------------------|---------|
| 条款域 clause | `com.titanium.clause.api.ClauseClient` | `ClauseService` | `ClauseServicePort` | `ClauseServiceAdapter` |
| 核保域 underwriting | `com.titanium.underwriting.api.UnderwritingApi` | `UnderwritingService` | `UnderwritingServicePort` | `UnderwritingServiceAdapter` |
| 产品域 product | `com.titanium.product.api.ProductApi` | `ProductService` | `ProductServicePort` | `ProductServiceAdapter` |
| 规则引擎域 rule-engine | `com.titanium.ruleengine.api.RuleEngineApi` | `RuleEngineService` | `RuleEngineServicePort` | `RuleEngineServiceAdapter` |

> 说明：依赖采用「Service 客户端 + 领域端口 + Adapter」的防腐层模式，领域层只依赖 `*ServicePort` 接口。
> **客户域 customer 无 Feign 依赖**：`customerId` 仅作为数据字段存储于 `ProposalBasicInfo`/`InsuranceBasicInfo`，不发起远程调用。

---

## 三、文件锁定建议（高频冲突区）

以下文件为聚合核心，**同一时刻只允许一个 writer**，改动需串行：

| 锁定优先级 | 文件 | 冲突原因 |
|-----------|------|---------|
| 🔴 高 | `titanium-policy-domain/.../aggregate/Policy.java` | 命令/事件/状态机集中，改任一保单行为都触此 |
| 🔴 高 | `titanium-policy-domain/.../aggregate/Insurance.java` | 核保对接、承保出单逻辑集中 |
| 🔴 高 | `titanium-policy-domain/.../aggregate/Proposal.java` | 意向单状态机 + 纯对象方法双轨 |
| 🟠 中 | `titanium-policy-application/.../saga/IssuanceSaga.java` | 跨聚合编排，依赖事件契约稳定 |
| 🟠 中 | `titanium-policy-query/.../handler/PolicyProjectionEventHandler.java` | 读模型投影，随事件结构联动 |
| 🟡 低 | `titanium-policy-common/.../constant/PolicyConstants.java` | 共享常量/Kafka topic，追加为主 |
| 🟡 低 | `titanium-policy-domain/.../command/*`、`event/*` | record 文件粒度小，按文件分配可并行 |

`command/`、`event/` 下每个 record 独立成文件，可按「一人一文件」并行；但**新增/修改 event 字段必然牵动聚合根 + 投影 + Mapper**，需与持有聚合根锁的 Agent 协调。

---

## 四、Agent 任务分工建议

按聚合边界纵向切分，降低跨文件耦合：

- **Agent-Proposal**：负责 `Proposal` 聚合及其 command/event/entity（proposal 子包）、`ProposalApplicationService`、`ProposalProjection`、`ProposalMapper`。
- **Agent-Insurance**：负责 `Insurance` 聚合及其 command/event/entity（insurance 子包）、`InsuranceApplicationService`、核保对接（`UnderwritingService*`）、`InsuranceProjection`。
- **Agent-Policy**：负责 `Policy` 聚合、policy 级 command/event、`PolicyApplicationService`、读侧（`PolicyQueryHandler`/`PolicyProjectionEventHandler`/`PolicyView`）。
- **Agent-Saga/集成**：负责 `IssuanceSaga`、`KafkaEventPublisher`、四个外部 Service/Adapter、`AxonConfig`/`KafkaConfig`。改 Saga 前需先确认三个聚合的事件契约已冻结。

跨聚合改动（如新增「续保」链路涉及 `PolicyRenewedEvent`）应由一个 Lead Agent 统筹，避免事件契约多头修改。

---

## 五、协作检查清单

### 改聚合根时必须同步检查：

- [ ] **命令**：新增行为是否需要新 `XxxCommand`（record，含 tenantId）？置于 `domain/command/`。
- [ ] **事件**：状态变更是否需要新 `XxxEvent`（record，含 tenantId）？置于 `domain/event/` 对应子包。
- [ ] **EventSourcingHandler**：聚合根内是否补了 `@EventSourcingHandler on(新事件)` 以正确回放？
- [ ] **读模型投影**：`PolicyProjectionEventHandler` 是否需新增 `@EventHandler` 投影到 `PolicyView`？（Insurance/Proposal 目前无事件投影，仅写侧 QueryHandler）
- [ ] **Kafka 发布**：下游是否依赖该事件？需在 `KafkaEventPublisher` 补发布 + `PolicyConstants.KafkaTopic` 加 topic。
- [ ] **MapStruct**：聚合↔Entity↔DTO 字段变化是否需更新 `PolicyMapper`/`InsuranceMapper`/`ProposalMapper`？
- [ ] **JPA 实体 / Liquibase**：持久化字段变化是否需改 `*Entity` 并新增 liquibase changelog（`bootstrap/resources/liquibase/`）？
- [ ] **应用层编排**：`*ApplicationService` 是否需暴露新方法、`*Controller` + `PolicyApi` 是否需新端点？
- [ ] **测试**：更新 `PolicyTest` / `ProposalTest`（domain 层），新行为补单测；Insurance 当前缺测试类，新增行为时建议补 `InsuranceTest`。
- [ ] **租户维度**：新查询/投影方法是否带 `tenantId` 过滤？

### 集成契约变更（影响其他域）必须广播：

- [ ] 改 `PolicyApi` 接口签名 → 通知所有调用方域（影响 Feign 契约）。
- [ ] 改外发 Kafka 事件结构（`PolicyCreatedEvent`/`PolicyActivatedEvent`）→ 通知下游消费方。
- [ ] 依赖的上游 Feign 接口（Clause/Underwriting/Product/RuleEngine api）变更 → 同步升级对应 `*Service` 封装。

---

## 六、关键事实速查

- 聚合根方法数：Policy 8 命令处理器 / 8 事件溯源处理器（+5 业务方法）；Insurance 5/4；Proposal 3/4（+纯对象方法）。
- 命令 16 个、事件 19 个、查询 6 个（写侧 3 + 读侧 3）。
- 外部 Feign 依赖 4 个：clause、underwriting、product、rule-engine（无 customer 远程调用）。
- 对外暴露 1 个 Feign 接口 `PolicyApi`；实际外发 Kafka 事件 2 个。
- 跨聚合编排唯一入口：`IssuanceSaga`（insuranceId 关联）。
