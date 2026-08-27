# Policy 模块设计说明

## 设计目标

在租户隔离和幂等约束下完成产品驱动出单，并把关键业务数据完整投影到保单查询模型。

## 方案选择

- Policy 负责流程编排和保单聚合，不把费率规则复制到本域。
- 按产品配置选择 ONE_STEP、TWO_STEP 或 THREE_STEP，阶段推进由出单进度读模型记录。
- 出单后通过事件驱动 Customer、Underwriting、Billing、Payment 等域的协作。

## 关键决策

- `bizNo + tenantId` 是出单幂等键；关键关联 ID 必须进入进度表和响应。
- 保单保存产品、条款、责任和标的快照，保证后续产品版本变更不影响历史保单。
- 收费未完成时保单进入 `PENDING_EFFECTIVE`/`PENDING_COLLECTION`，禁止伪造已收款状态。

## 保全字段目录

- Policy 拥有稳定字段码、对象类型、数据类型、敏感级别、掩码策略和执行能力事实。
- `/api/v1/policy-field-catalogs/current` 只返回目录元数据，不暴露实例值、JSON Path、数据库列或命令映射。
- 目录按字段码排序后生成 SHA-256；版本、哈希、租户和查询业务时点随响应返回。
- `proposable` 仅表示允许形成拟变更；`executionSupported` 表示是否已具备真实回写映射，两者不得混用。
- 目录版本 `2026.08.25.1` 首批开放 `policy.holder.mobile` 真实执行；其余 P0 字段可提案但继续标记为未支持执行。

## 保全正式应用边界

- `/api/v1/policies/{policyId}/maintenance-applications` 接收来源案件冻结的结构化变更，不接收调用方自报批单、实际
  版本、应用摘要或 applied 值。
- Policy 聚合先按 `requestId` 查找既有回执；同摘要和同期望版本直接返回，异载荷报幂等冲突。该检查先于保单状态和
  当前版本校验，保证响应丢失后可恢复同一批单。
- 新请求独立复算完整载荷 SHA-256、校验当前版本和立即生效语义，再由字段执行器修改合同子状态。字段型案件继续追加
  `PolicyMaintenanceAppliedEvent`；状态类案件先完整校验字段与状态转换，再追加单个
  `PolicyMaintenanceStateAppliedEvent`，原子形成字段实际值、状态前后值、批单、版本、应用摘要和 applied 快照引用。
- 聚合和查询服务复用 `PolicyMaintenanceHashing.snapshotHash`，保证事件回执与后续正式快照查询使用同一结构化摘要。
- Policy 只实现中立正式契约，不读取 Maintenance 数据；案件流程、任务、失败恢复和多项目编排仍属于 Maintenance。

## 保全建案与未来日期快照

- 保全建案快照必须返回 Policy 基准版本、产品版本、计划版本、事件快照引用、内容摘要和结构化字段值，调用方不得使用
  保单列表或展示 DTO 拼装建案证据。
- `nextBillingDateAt` 按主险缴费频率与缴费周期锚点推导；趸交保单返回空。`nextPolicyAnniversaryAt` 按保单起期推导。
  两个字段均为可空 `OffsetDateTime`，追加在正式响应尾部并保留旧构造器，兼容 M5-04 之前的客户端和测试夹具。
- Policy 只提供权威日期事实，不创建或扫描未来计划。Maintenance 冻结租户 `ZoneId`、换算 UTC 执行时间，并在到期后
  重新取得 Policy 快照完成版本和状态勾稽。
- 正式应用接受 `IMMEDIATE` 及四种已到期计划时态：`FUTURE`、`SPECIFIED_DATE`、`NEXT_BILLING_DATE`、
  `POLICY_ANNIVERSARY`。计划是否到期由 Maintenance 使用冻结的租户时区和 UTC 计划校验，避免 Policy 使用服务器墙钟
  误判异地租户；Policy 拒绝 `RETROACTIVE` 和未知时态，并对 `IMMEDIATE` 保留 5 分钟前置保护。

## 保全关联只读边界

- Policy 不新增对 Maintenance 的模块或远程依赖，避免形成 Policy 与 Maintenance 的反向循环依赖。
- `/web/v1/policies/{policyId}/maintenance-cases` 从租户隔离的批改投影派生，只返回非空
  `sourceMaintenanceId`，按案件 ID 保序去重。
- 返回值表示保全变更已形成 Policy 批改事实，不表示案件当前流程状态；在途查询、建案和操作仍只在保全管理页面提供。

## 安全边界

- Controller 校验租户头和请求字段；Repository/Projection 查询必须带 tenantId。
- 正式应用同时校验路径 Policy、聚合租户、稳定请求 ID、请求 SHA-256 和期望版本；字段执行能力来自注册表白名单。
- 生产环境必须启用认证授权、CSRF/签名等安全策略；本地验收栈的安全排除不得复用到生产。

## 已知限制

- 当前出单测试以在线收费为主，支付成功回调后的正式生效流程仍需扩展验收覆盖。
- 字段目录首版为平台标准目录，产品/保单类型和租户覆盖尚未落地；查询条件会完整校验和回显。
- 当前正式保全应用支持 `IMMEDIATE` 和四种已到期计划时态、`policy.holder.mobile` 及暂停、恢复、复效和终止状态动作；
  追溯时态、未来版本预写和其他字段执行器尚未开放。

## 变更历史

### 2026-08-19 - 完成三种出单模式验收

验证一步、两步、三步模式的数据落库、跨域关联、死信和 tracking processor 追平。

### 2026-08-24 - 发布保全字段目录 V1

新增版本化字段目录、共享元数据枚举、只读 API 和契约测试，为 Maintenance 配置发布提供权威字段能力证据。

### 2026-08-25 - 发布保全案件只读关联

从 Policy 本地批改投影派生已生效保全引用，不引入 Maintenance 反向依赖或保全操作入口。

### 2026-08-25 - 发布保全正式应用 API

新增稳定请求幂等、期望版本并发控制、结构化字段执行器、批单与 applied 快照权威回执；首批真实应用投保人手机号，
并保持 Policy 对 Maintenance 零反向依赖。

### 2026-08-25 - 发布状态类保全原子应用

正式应用请求新增可选状态动作、原因和终止原因；聚合复用既有状态机前置条件并以独立追加事件同时写入目标状态、版本、
批单和实际快照。统一回执新增状态前后值，查询投影同步刷新状态和批单；M5-02 历史事件结构保持不变。

### 2026-08-25 - 发布保全未来日期快照与到期应用契约

保全建案快照新增下一缴费日和下一保单周年日；Policy 正式应用开放四种计划时态，继续拒绝追溯生效和未知时态。计划、
租户时区、提前执行门禁、可靠租约及到期重校验仍由 Maintenance 负责，Policy 不引入 Maintenance 反向依赖。
