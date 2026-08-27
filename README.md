# Titanium Policy Domain

Policy 是保险出单和保单生命周期域，负责投保单、投保申请、保单签发以及险种、标的、条款和责任的保单快照。

## 核心职责

- 编排一步、两步、三步出单流程。
- 创建客户关联、投保单/保单及其业务快照。
- 消费核保、计费和支付结果，推进保单状态。
- 通过正式保全应用契约校验请求摘要和期望版本，原子应用案件字段及暂停、恢复、复效、终止状态动作，并生成统一批单、版本及实际快照回执。
- 从本域批改投影只读展示已在保单落地的保全案件引用。

## 边界与依赖

客户主数据由 Customer 管理，产品定价由 Product 管理，账单由 Billing 管理，核保由 Underwriting 管理。跨域通过 API 和领域事件协作。

Policy 不依赖 Maintenance。`GET /web/v1/policies/{policyId}/maintenance-cases` 仅返回当前租户批改记录中的去重
`sourceMaintenanceId` 及批单、生效日和保单版本；不返回在途案件，也不提供保全创建或操作入口。

服务间正式应用入口为 `POST /api/v1/policies/{policyId}/maintenance-applications`。请求携带稳定请求 ID、来源案件、
期望版本、完整载荷 SHA-256、proposed 快照摘要、生效语义、结构化字段变化和可选状态动作；响应返回批单、实际版本、
应用摘要、applied 快照引用、实际字段值以及状态前后值。当前支持立即生效，以及已到执行时点的 `FUTURE`、
`SPECIFIED_DATE`、`NEXT_BILLING_DATE`、`POLICY_ANNIVERSARY`；提前执行、追溯生效和未知时态失败关闭。字段执行首批开放
`policy.holder.mobile`，状态动作支持 `SUSPEND`、`RESUME`、`REINSTATE`、`TERMINATE`；其他字段按字段目录能力失败关闭。

保全建案正式快照在基准版本、产品/计划版本、快照引用和结构化字段值之外，追加可空的 `nextBillingDateAt` 与
`nextPolicyAnniversaryAt`。前者按 Policy 缴费频率和缴费周期锚点推导，趸缴情形为空；后者按保单起期推导。两者均为
带偏移量的权威业务时点，只向 Maintenance 提供日期事实；租户时区冻结、计划持久化、租约和到期执行仍由 Maintenance
负责。

## 快速使用

```bash
cd /Users/sunwei/titanium-project/titanium-policy
mvn -q -DskipITs verify
```

本地出单接口由 `titanium-policy` 暴露在 `8080` 端口。
