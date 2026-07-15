# titanium-policy 保单域 - 模块开发规约

> 适用范围：仅 `titanium-policy` 微服务
> 上级规约：根目录 [CLAUDE.md](../CLAUDE.md)（全局通用约定不在此重复）
> 本文档内容均基于模块实际代码探查，非臆测

---

## 一、模块概述

保单域是 Titanium 保险核心系统的**承保执行中枢**，负责保险全生命周期中
「投保意向 → 投保单承保 → 正式保单生命周期」这一主链路。三个聚合根对应三个业务阶段：

| 聚合根 | 业务阶段 | 状态机 |
|--------|---------|--------|
| `Proposal`（投保意向单） | 客户初步投保意愿登记 | DRAFT → SUBMITTED → CONVERTED_TO_APPLICATION / VOIDED |
| `Insurance`（投保单） | 投保信息、核保对接、承保出单 | DRAFT → UNDERWRITING → UNDERWRITING_APPROVED/REJECTED/SUSPENDED → ISSUED |
| `Policy`（正式保单） | 保单全生命周期 | NOT_EFFECTIVE → EFFECTIVE → SUSPENDED/TERMINATED/EXPIRED/CANCELLED |

保单域定位为「执行者」：被动接收核保结果、保全域指令、计费域缴费记录，驱动自身状态流转。

---

## 二、技术栈与运行参数

| 项 | 值 | 来源 |
|----|----|------|
| JDK | Amazon Corretto 21 | 根规约 |
| Spring Boot | 4.0.1 | 根规约 |
| Axon Framework | 4.10.0（CQRS + Event Sourcing + Saga） | 实际代码 |
| 服务端口 | **8080** | `application.yml:2` |
| 应用名 | `titanium-policy` | `application.yml:6` |
| 数据库 | MySQL，库名 **`policy_db`** | `application.yml:10` |
| 序列化 | Axon Jackson serializer | `application.yml:55` |
| Event Store | JPA（`axon.eventstore.jpa`） | `application.yml:72` |
| 事件处理组 | `policy-query-group`（TRACKING + DLQ 已开启） | `application.yml:61` |
| Kafka | `localhost:9092`，消费组 `policy-group` | `application.yml:27` |
| Redis | `localhost:6379`，查询缓存 | `application.yml:39` |
| Liquibase | `classpath:liquibase/changelog-init.xml` | `application.yml:50` |
| 多租户 | `X-Tenant-Id` 请求头贯穿；所有事件携带 `tenantId` | 实际代码 |

---

## 三、子模块分层结构

```
titanium-policy/
├── titanium-policy-api/            # 对外 Feign 接口 + DTO（PolicyApi、PolicyDTO、CreatePolicyDTO…）
├── titanium-policy-common/         # PolicyConstants（含 KafkaTopic）、PolicyUtils
├── titanium-policy-domain/         # 领域层（核心）
│   ├── aggregate/                  # Proposal / Insurance / Policy 三个聚合根
│   ├── command/                    # 16 个命令（record）
│   ├── event/{,insurance,proposal} # 19 个领域事件（record）
│   ├── entity/{,insurance,proposal}# 聚合内实体（PolicyItem、Subject、ProposalHolder…）
│   ├── valueobject/                # 值对象（PolicyStatus、Amount、PolicyNo、PremiumPlan…）
│   ├── repository/                 # 仓储接口（PolicyRepository…）
│   └── service/                    # 领域服务 + 端口接口（ClauseServicePort 等防腐端口）
├── titanium-policy-application/    # 应用层
│   ├── command/                    # PolicyApplicationService / InsuranceApplicationService / ProposalApplicationService
│   ├── query/                      # *AppQueryService（查询编排）
│   ├── saga/                       # IssuanceSaga（出单流程编排）
│   └── service/                    # 外部服务客户端 + Adapter（Clause/Underwriting/Product/RuleEngine）
├── titanium-policy-infrastructure/ # 基础设施层（写侧纯事件溯源，无 JPA 写模型）
│   ├── adapter/                    # 外部域调用 Adapter（Clause/Product/Underwriting/RuleEngine/Billing）
│   ├── event/                      # KafkaEventPublisher（领域事件 → Kafka 外发）
│   ├── messaging/                  # 跨域事件监听器
│   ├── generator/                  # 业务凭证号生成器实现
│   └── config/                     # AxonConfig / KafkaConfig
├── titanium-policy-query/          # CQRS 读侧（读路径垂直切片，依赖 domain+common，不依赖 application/infra）
│   ├── view/                       # 读模型实体 XxxView（extends BaseView，映射 t_xxx_view）
│   ├── handler/
│   │   ├── projection/             # 事件投影 XxxProjectionEventHandler（@EventHandler，事件→读模型）
│   │   └── query/                  # Axon 查询处理 XxxQueryHandler（@QueryHandler）
│   ├── service/                    # 读模型查询实现 XxxQueryService(Impl)（复杂查询内聚，Specification 动态组装）
│   ├── query/                      # 查询入参 FindXxxQuery（record）
│   ├── result/                     # 查询出参 XxxQueryResult（DTO）
│   ├── repository/                 # 读模型仓储 XxxViewRepository（Spring Data，无需手写 impl）
│   ├── config/                     # 读侧专属配置（查询缓存，不上提 common）
│   └── scheduled/                  # DeadLetterQueueService（DLQ 投影重试，读模型最终一致）
└── titanium-policy-bootstrap/      # 启动/组合根（PolicyApplication + 依赖 web+infrastructure 装配实现）
```

> 仅 `bootstrap` 打可执行 jar，其余模块作为依赖引入（参见根规约第七章）。
> query 读侧包组织/命名/两套查询区分详见根规约 3.4.9 与 [docs/Query层架构定位与包组织规范.md](../docs/Query层架构定位与包组织规范.md)。

---

## 四、核心领域模型

### 4.1 命令清单（16 个，均为 record）

| 聚合 | 命令 |
|------|------|
| Proposal | `CreateProposalCommand`、`SubmitProposalCommand`、`VoidProposalCommand` |
| Insurance | `ConvertProposalToInsuranceCommand`、`CreateInsuranceDirectlyCommand`、`SubmitUnderwritingCommand`、`ReceiveUnderwritingResultCommand`、`TriggerIssuanceCommand` |
| Policy | `CreatePolicyCommand`、`CreatePolicyDirectlyCommand`、`IssuePolicyCommand`、`ActivatePolicyCommand`、`SuspendPolicyCommand`、`ResumePolicyCommand`、`TerminatePolicyCommand`、`CancelPolicyCommand` |

### 4.2 事件清单（19 个，均为 record）

| 聚合 | 事件 |
|------|------|
| Proposal | `ProposalCreatedEvent`、`ProposalSubmittedEvent`、`ProposalConvertedEvent`、`ProposalVoidedEvent` |
| Insurance | `InsuranceCreatedEvent`、`InsuranceSubmittedForUnderwritingEvent`、`UnderwritingResultReceivedEvent`、`InsuranceIssuedEvent` |
| Policy | `PolicyCreatedEvent`、`PolicyIssuedEvent`、`PolicyActivatedEvent`、`PolicySuspendedEvent`、`PolicyResumedEvent`、`PolicyTerminatedEvent`、`PolicyExpiredEvent`、`PolicyCancelledEvent`、`PolicyPaymentRecordedEvent`、`PolicyDataUpdatedEvent`、`PolicyRenewedEvent` |

### 4.3 查询清单（6 个）

- 写侧（domain/query，按编号精确查）：`InsuranceQuery`、`ProposalQuery`、`PolicyQuery`
- 读侧（query/query，读模型查）：`FindPolicyByIdQuery`、`FindPoliciesByCustomerQuery`、`FindPoliciesByMultipleConditionsQuery`

### 4.4 聚合根行为（充血模型）

- **Policy**（`aggregate/Policy.java`）：8 个 `@CommandHandler`（2 个构造器命令 + 6 个 handle）、8 个 `@EventSourcingHandler`；另含非命令业务方法 `expire()`（定时任务触发到期）、`recordPayment()`（计费域触发缴费）、`updatePolicyStatus()`/`linkSubPolicy()`（父子保单联动）、`incrementVersion()`（保全域变更）。命令处理器内含严格状态前置校验（如签发仅允许 NOT_EFFECTIVE、激活需首期保费已缴）。
- **Insurance**（`aggregate/Insurance.java`）：5 个 `@CommandHandler`、4 个 `@EventSourcingHandler`；`UnderwritingResultReceivedEvent` 用 `switch` 表达式按核保结果码映射目标状态（APPROVED/REJECTED/SUSPENDED）。
- **Proposal**（`aggregate/Proposal.java`）：3 个 `@CommandHandler`、4 个 `@EventSourcingHandler`；额外提供一套**纯对象方法**（`createDraft`/`submitProposal`/`voidProposal`/`convertToApplication`/`addSubject`/`addApplicant`），供应用层与单元测试以非事件溯源方式构建。

### 4.5 IssuanceSaga 出单流程编排

`application/saga/IssuanceSaga.java` 以 `insuranceId` 为关联键，打通「投保单承保出单 → 正式保单创建」此前断裂的链路：

```
InsuranceCreatedEvent  --@StartSaga--> 记忆建单数据（投保人/保费/保障期间/形态）
        ...（核保提交、核保结果回流为跨服务同步调用，暂未纳入 Saga 异步编排）
InsuranceIssuedEvent   --@EndSaga----> commandGateway.sendAndWait(CreatePolicyCommand) 创建正式保单
```

注意：Saga 用 `@Autowired transient` 注入 `CommandGateway`/`PolicyNoGenerator`（Saga 不可序列化字段必须 transient）。核保结果跨 `titanium-underwriting` 的 Kafka 异步回流尚未落地，落地后可在此追加 `UnderwritingResultReceivedEvent` 的 `@SagaEventHandler` 实现核保→承保全自动流转。

---

## 五、编码规约（本模块实例）

继承根规约，以下为结合本模块代码的强约束：

- **命令/查询用 record**：所有 command/event/query 均为 record，如 `public record CreatePolicyCommand(...)`。
- **命令处理走 Axon**：写操作用 `@CommandHandler`，事件回放用 `@EventSourcingHandler`，发布事件用 `AggregateLifecycle.apply(...)`。禁止在聚合根外直接 new 事件落库。
- **充血模型**：业务规则（状态校验、状态流转）内聚到聚合根，应用层只做编排，不写业务判断。参考 `Policy.handle(ActivatePolicyCommand)` 的多重前置校验。
- **构造器注入优先**：QueryHandler/EventHandler 用 `@RequiredArgsConstructor`（见 `PolicyProjectionEventHandler`、`KafkaEventPublisher`）。
- **写侧纯事件溯源（无 JPA 写模型）**：三聚合（Policy/Insurance/Proposal）均为 `@Aggregate`，状态只在 Axon 事件流，命令链路由 Axon 自动装配的 `EventSourcingRepository` 持久化事件。🔴 已删除全部残留 JPA 写侧死码：`infrastructure/{entity,repository,repository.jpa,mapper}` 及 domain 的 `PolicyRepository`/`InsuranceRepository`/`ProposalRepository` 三个无消费者端口（详见 `docs/技术文档/写侧收敛与DO命名收敛手册.md`、`持久化选型规范(JPA与EventSourcing).md`）。写侧不再有 `*Entity`/`Jpa*Repository`/写侧 Mapper。
- **JPA 仅服务读侧**：`@EntityScan`/`@EnableJpaRepositories` 仅扫描 `query.view`/`query.repository`（读模型 `*View` + `*ViewRepository`）。唯一性/存在性校验如有需要，走读模型 View（最终一致）。
- **读侧转换走 MapStruct（如有）**：读模型 View↔QueryResult 转换用 MapStruct，禁止手写实体互转。
- **SLF4J 占位符**：`log.info("[IssuanceSaga] 启动: insuranceId={}", event.insuranceId())`，禁止字符串拼接。
- **中文注释**：类/方法注释中文，标识符英文（全模块已遵循）。
- **租户贯穿**：命令、事件、查询、读模型全部携带 `tenantId`；读模型查询用 `findByPolicyIdAndTenantId` 等带租户维度方法。

### 5.1 包组织强制规范（整改后，遵循根规约 3.4）

本模块已按根规约第 3.4 节整改，结构如下，新增代码必须遵守：

- **枚举归属**：本域专属枚举统一在 `titanium-policy-common` 的 `enums` 子包；跨域共享枚举在 `titanium-metadata/enums/policy`。**domain/valueobject 内禁止再定义枚举**（原 8 个枚举已迁出）。
- **domain/valueobject**：仅放 record 值对象（含 `IssuanceRequest`/`IssuanceResult`，已从 service 迁入）。
- **domain/port（🆕 与 aggregate 平级）**：远程调用 Port，`ProductServicePort`/`ClauseServicePort`/`UnderwritingServicePort`/`RuleEngineServicePort`/`UnderwritingDecisionGateway`（六边形架构 Port，**保留后缀，非命名错误**）。5 个平铺，>8 个再按 `port/remote` 二级子包拆。
- **domain/generator（🆕 与 aggregate 平级）**：`PolicyNoGenerator`/`EndorsementNoGenerator` 领域凭证号生成契约。
- **domain/repository**：仓储接口保持独立（本质也是 driven port，位置尊重 DDD 惯例，不并入 port）。
- **domain/service（🔴 只放跨聚合纯领域服务）**：判定铁律「三无 + 一不属于」——无 CommandGateway、无外部 Port、无基础设施依赖，且不属于任何单个聚合根。
  - ✅ **正面样板**：`PolicyIssuanceDomainService`/`PolicyIssuanceDomainServiceImpl`（`/service` + `/service/impl`），承载「投保单 `Insurance` + 核保结果 `UnderwritingResult` → 承保决策 `PolicyIssuanceDecision`」跨聚合纯规则，可脱离 Spring 用 `new` 直测（见 `PolicyIssuanceDomainServiceTest`）。
  - 🔴 **反例（已删）**：原 `PolicyService`/`PolicyServiceImpl` 五方法均以单个 `Policy` 为首参、与聚合根 `handle(ActivatePolicyCommand)` 校验/`updatePolicyStatus(...)` 重复、零调用方，属贫血伪装，已删除（能力在聚合根内）。判据：方法首参是聚合根 = 应内聚到聚合根。
  - 🔴 **禁止依赖 Port**：ArchUnit 第 8 条断言 `domain.service` 不得依赖 `domain.port`，违反即 `mvn test` 失败。边界详见 [docs/DDD-领域服务与应用层边界指南.md](../docs/DDD-领域服务与应用层边界指南.md)。
- **application/orchestration（🆕 编排器归属）**：跨聚合编排 + 发命令 + 调 Port 的应用服务。`IssuanceOrchestrator`/`RiskAssessmentExecutor`（原 `domain/service` 下伪领域服务迁入）；✅ `PolicyIssuanceOrchestrator`（承保编排样板：取号→调 `PolicyIssuanceDomainService` 拿决策→发 `CreatePolicyCommand`，构造器注入，无业务判断）。
- **Port/Adapter 分层**：Port 接口在 domain；调用 clause/product/underwriting/rule-engine 等外部域的 **Adapter 实现在 `titanium-policy-infrastructure/adapter`**（已从 application 层迁入），直接调用对应域的 Feign 客户端，**不再有中间转调层**。application 层只编排、注入 Port 使用。

### 5.2 Application 层四包契约与编排范式（遵循根规约 3.4.8）

> 完整理论与本域样板见 [docs/DDD-应用层读写分离与流程编排指南.md](../docs/DDD-应用层读写分离与流程编排指南.md)。本域 application 层四包**不是并列入口**，而是「入口维度 + 编排机制维度」的正交产物。

- **入口维度（web 只调这两个）**：
  - `application/command`：`PolicyApplicationService`/`InsuranceApplicationService`/`ProposalApplicationService`——写用例入口门面，薄。单命令直发 `commandGateway.sendAndWait`；复杂用例委托 orchestration/saga。
  - `application/query`：`*AppQueryService`——读用例入口，查读模型（🔴 现状仍查写模型聚合 `PolicyRepository`，属遗留，整改中改查 `PolicyView`）。
- **编排机制维度（web/api 禁止直接依赖）**：
  - `application/orchestration`：**同步命令式**编排。`IssuanceOrchestrator`（出单模式路由/一步出单）、`PolicyIssuanceOrchestrator`（承保编排样板：取号→调领域服务拿决策→发命令，零业务判断）、`RiskAssessmentExecutor`（风控步骤）。
  - `application/saga`：**异步事件驱动**编排。`IssuanceSaga`（全仓唯一 `@Saga`，关联键 `insuranceId`，投保→核保→承保→出单）。由领域事件经 Axon 自动触发，不被主动调用。
- **两范式选择判据**：跨服务/长周期/需补偿 → **Saga**；步骤明确、同步即时返回、不跨服务 → **Orchestrator**。二者是同一「跨聚合编排」职责的两种一致性形态。**出单流程既定收敛为：一步出单走 Orchestrator，两步/三步全程走 Saga**（详见指南第七章方案 A）。
- **命名纪律**：允许 `Orchestrator`/`Saga`/`Service` 后缀；🔴 **禁用 `Handler`/`Processor`**（与 Axon `@CommandHandler`/`EventProcessor` 撞名）。
- **构建期硬约束（ArchUnit）**：web/api 不得依赖 orchestration/saga、api 不得依赖内部层、application 禁 Handler/Processor 后缀——违反即 `mvn test` 失败（见根规约 3.4.8 ④）。


---

## 六、构建与运行

```bash
# 设定 JDK 21
export JAVA_HOME=/Users/sunwei/Library/Java/JavaVirtualMachines/corretto-21.0.4/Contents/Home

# 在项目根目录构建（保单域依赖 metadata/clause/underwriting/product/ruleengine 的 api 模块）
cd /Users/sunwei/titanium-project
mvn -pl titanium-policy/titanium-policy-bootstrap -am clean install -DskipTests

# 单独启动保单服务（端口 8080）
cd titanium-policy/titanium-policy-bootstrap
mvn spring-boot:run

# 运行领域层单元测试（PolicyTest / ProposalTest）
cd /Users/sunwei/titanium-project
mvn -pl titanium-policy/titanium-policy-domain test
```

启动前置：MySQL（`policy_db`）、Kafka（9092）、Redis（6379）需就绪。`application.yml` 中 `axon.axonserver` 段配置了 AxonServer 集群/SSL/Token，本地若不连 AxonServer 需相应调整或走嵌入式 Event Store。

---

## 七、已知缺陷与注意事项（基于代码实况）

> 架构整改（读写分离/编排归位/严格隔离）已完成多轮，本清单已同步最新状态：✅=已整改，⚠️=仍存在。

1. ✅ **Controller 查询已接读模型**：`PolicyController.getPolicy`、`InsuranceController.getInsurance`、`ProposalController.getProposal` 已经 `*AppQueryService` 走 `QueryGateway` 查读模型 View。⚠️ 仅 `PolicyController.getPoliciesByCustomerId/getPoliciesByStatus/getAllPolicies` 因 `PolicyApi` 契约缺 tenantId/分页参数暂未接通（已注释标注待 API 补齐）。
2. ✅ **Insurance/Proposal 读模型投影已补齐**：`InsuranceProjectionEventHandler`/`ProposalProjectionEventHandler`（query 层）以 `@EventHandler` 投影到 `t_insurance_view`/`t_proposal_view`，`*AppQueryService` 走 QueryGateway 查读模型，实现真正读写分离（原直查写侧 JPA 的 `InsuranceProjection`/`ProposalProjection` 脏类已删除）。
3. ⚠️ **Kafka 仅发布 2 个事件**：`KafkaEventPublisher` 只外发 `PolicyCreatedEvent`、`PolicyActivatedEvent`，其余事件不出域。下游若依赖保单状态需补充发布。
4. ⚠️ **孤儿事件**：`PolicyDataUpdatedEvent`、`PolicyRenewedEvent` 已定义但无任何命令产生、无 handler 消费（续保/数据变更链路未实现）。
5. ✅ **ProposalConvertedEvent 写侧已补齐**：新增 `ConvertProposalCommand` + `Proposal.handle(ConvertProposalCommand)`（仅 SUBMITTED 可转，发布 `ProposalConvertedEvent`）。读模型 `ProposalProjectionEventHandler` 投影已就绪，转换命令触发后自动生效。纯对象方法 `convertToApplication` 保留供非事件溯源构建。
6. ✅ **表现层不再依赖领域命令**：`PolicyController` 等三个 Controller 的命令构造已下沉至 `*ApplicationService`（表现层只传 Request/api-DTO，不持有 domain command），并以 ArchUnit `webShouldNotDependOnDomainCommandsOrAggregates` 固化。
7. ⚠️ **核保回流未异步化**：Saga 注释说明核保结果跨服务回流依赖消息总线基础设施，尚未落地，当前为同步调用。
8. ✅ **事件存储与投影脏数据已修**：原 `AxonConfig` 无条件 `InMemoryEventStorageEngine`（事件溯源重启丢事件）已删除，交还 Axon Starter 依 `application.yml` 的 `axon.eventstore.jpa` 装配；原 `PolicyProjection` 向 `t_policy` 写空实体脏数据的投影类已删除（读模型投影统一在 query 层）。

---

*改动聚合根/事件/命令前，请同步阅读 [AGENTS.md](./AGENTS.md) 的协作检查清单与文件锁定建议。*
