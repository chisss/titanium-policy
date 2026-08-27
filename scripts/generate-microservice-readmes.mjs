import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const projectRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../..");
const githubOrg = "https://github.com/chisss";

const services = [
  {
    id: "admin",
    repo: "titanium-admin",
    githubRepo: "titanium-admin",
    name: "管理后台服务",
    group: "平台与运营",
    domainType: "支撑域",
    summary: "为运营人员提供认证、租户、用户、角色、菜单、字典、审计及跨域管理代理能力。",
    models: "Tenant、AdminUser、Role、Menu",
    upstream: "管理端、API 网关",
    downstream: "Product、Rule Engine 等业务服务",
    port: "8090",
    contextPath: "/",
    owns: ["后台认证与 JWT 签发", "租户、用户与 RBAC 权限管理", "菜单、字典与操作审计", "受控的跨域后台代理与响应脱敏"],
    excludes: ["保险业务规则与聚合状态", "下游服务的数据所有权", "面向客户的交易流程"],
  },
  {
    id: "billing",
    repo: "titanium-billing",
    githubRepo: "titanium-billing",
    name: "计费域",
    group: "交易与履约",
    domainType: "支撑域",
    summary: "管理保费应收、账单、收费计划、税务台账、佣金应付及生命周期差额入账。",
    models: "Bill、PremiumReceivable、LifecyclePosting",
    upstream: "Product、Policy、Maintenance",
    downstream: "Payment、Channel",
    port: "8083",
    contextPath: "/billing",
    owns: ["账单与应收账户", "分期、税务及佣金账务事实", "生命周期差额入账与冲正", "收款、退款来源与账务勾稽"],
    excludes: ["产品费率和保费公式", "支付渠道执行", "渠道合同资格判断"],
  },
  {
    id: "channel",
    repo: "titanium-channel",
    githubRepo: "titanium-channel",
    name: "渠道域",
    group: "生态支撑",
    domainType: "支撑域",
    summary: "管理代理人、经纪人、直销渠道及佣金合同，为销售和结算提供权威渠道事实。",
    models: "Channel、CommissionPlan、ChannelContract",
    upstream: "Admin、Product",
    downstream: "Billing、Policy",
    port: "8093",
    contextPath: "/",
    owns: ["渠道主体与层级", "渠道准入及有效期", "佣金方案与合同版本", "渠道销售能力查询"],
    excludes: ["保单销售流程", "佣金应付记账与付款", "产品定价"],
  },
  {
    id: "claim",
    repo: "titanium-claim",
    githubRepo: "titanium-claim",
    name: "理赔域",
    group: "交易与履约",
    domainType: "核心域",
    summary: "覆盖理赔报案、立案、调查、定损、责任审核、给付决定和结案的完整生命周期。",
    models: "ClaimCase、Compensation",
    upstream: "Policy、Customer、Document",
    downstream: "Payment、Regulatory、Reinsurance",
    port: "8083",
    contextPath: "/claim",
    owns: ["理赔案件与状态机", "事故、损失和材料证据", "责任认定与赔付决定", "结案及追偿事实"],
    excludes: ["保单合同解释原始定义", "资金渠道执行", "监管报表汇总"],
  },
  {
    id: "clause",
    repo: "titanium-clause",
    githubRepo: "titanium-clause",
    name: "条款域",
    group: "产品与承保",
    domainType: "核心域",
    summary: "管理保险条款、责任、告知内容与版本快照，为产品组合和保单合同提供可引用定义。",
    models: "InsuranceClause、Coverage、ClauseVersion",
    upstream: "Admin",
    downstream: "Product、Policy、Claim",
    port: "8083",
    contextPath: "/titanium-clause",
    owns: ["条款生命周期与版本", "保险责任和除外责任", "告知书与合同文本模板", "条款级结构化规则"],
    excludes: ["产品组合与销售计划", "最终保费计算", "出单后的合同实例"],
  },
  {
    id: "customer",
    repo: "titanium-customer",
    githubRepo: "titanium-customer",
    name: "客户域",
    group: "客户与交易",
    domainType: "通用域",
    summary: "维护自然人和组织客户主数据、身份、联系方式、关系及受益人信息。",
    models: "Customer、CustomerRelationship",
    upstream: "Admin、外部客户渠道",
    downstream: "Policy、Underwriting、Claim",
    port: "8081",
    contextPath: "/",
    owns: ["客户主数据与身份标识", "联系方式和地址", "家庭、组织及受益关系", "租户内客户去重与查询"],
    excludes: ["投保角色的合同快照", "核保风险结论", "保单与理赔状态"],
  },
  {
    id: "document",
    repo: "titanium-document",
    githubRepo: "titanium-document",
    name: "文档域",
    group: "生态支撑",
    domainType: "支撑域",
    summary: "统一管理保险业务文档的元数据、归档、版本、访问控制和业务关联。",
    models: "Document、DocumentVersion",
    upstream: "Policy、Claim、Maintenance",
    downstream: "对象存储、Notification",
    port: "8095",
    contextPath: "/",
    owns: ["文档元数据和版本", "业务对象关联", "归档状态与访问控制", "文件存储适配"],
    excludes: ["业务案件审批", "合同条款内容定义", "消息通知编排"],
  },
  {
    id: "feature-center",
    repo: "titanium-feature-center",
    githubRepo: "titanium-feature-center",
    name: "功能中心域",
    group: "平台与运营",
    domainType: "支撑域",
    summary: "提供租户级功能开关、灰度策略、能力授权和运行时特性判定。",
    models: "FeatureDefinition、FeatureToggle",
    upstream: "Admin",
    downstream: "全部业务服务",
    port: "8091",
    contextPath: "/",
    owns: ["功能定义与生命周期", "租户级开关覆盖", "灰度和生效窗口", "运行时功能判定"],
    excludes: ["业务规则计算", "用户 RBAC 权限", "动态配置中心"],
  },
  {
    id: "investment",
    repo: "titanium-investment",
    githubRepo: "titanium-investment",
    name: "投资域",
    group: "交易与履约",
    domainType: "核心域",
    summary: "承载投连险、万能险和分红险的投资账户、持仓、净值、申赎及账户价值计算。",
    models: "InvestmentAccount、FundHolding、UnitPrice",
    upstream: "Policy、Product、Payment",
    downstream: "Billing、Regulatory",
    port: "8092",
    contextPath: "/",
    owns: ["投资账户与持仓", "基金净值和账户估值", "申购、赎回与转换流水", "保障成本扣除和账户价值"],
    excludes: ["保单合同生命周期", "支付渠道清算", "基金外部交易系统"],
  },
  {
    id: "maintenance",
    repo: "titanium-maintenance",
    githubRepo: "titanium-maintenance",
    name: "保全域",
    group: "交易与履约",
    domainType: "核心域",
    summary: "管理保单生效后的变更案件，包括配置冻结、审核、核保、算费、结算和合同生效。",
    models: "MaintenanceCase、MaintenanceItem、WorkflowTask",
    upstream: "Policy、Customer、Product",
    downstream: "Underwriting、Billing、Payment",
    port: "8083",
    contextPath: "/maintenance",
    owns: ["保全案件和项目状态机", "字段变更、审核与核保证据", "保全报价和资金双门禁", "立即、未来及追溯生效编排"],
    excludes: ["保单权威合同状态", "产品定价公式", "账务与支付最终状态"],
  },
  {
    id: "notification",
    repo: "titanium-notification",
    githubRepo: "titanium-notification",
    name: "通知域",
    group: "生态支撑",
    domainType: "支撑域",
    summary: "统一处理短信、邮件、站内信等通知模板、发送任务、渠道路由和投递记录。",
    models: "NotificationRecord、Template、DeliveryTask",
    upstream: "全部业务服务",
    downstream: "短信、邮件及站内信渠道",
    port: "8094",
    contextPath: "/",
    owns: ["通知模板和变量", "发送任务与幂等", "渠道路由与重试", "投递状态和审计"],
    excludes: ["业务触发条件决策", "客户联系方式主数据", "第三方渠道内部实现"],
  },
  {
    id: "payment",
    repo: "titanium-payment",
    githubRepo: "titanium-payment",
    name: "支付域",
    group: "交易与履约",
    domainType: "支撑域",
    summary: "管理收款、退款、支付分配、渠道交互和资金状态，为账务与业务履约提供权威资金事实。",
    models: "PaymentOrder、RefundOrder、PaymentAllocation",
    upstream: "Billing、Maintenance、Claim",
    downstream: "支付渠道、Policy",
    port: "8096",
    contextPath: "/",
    owns: ["支付与退款订单", "支付渠道请求和回调", "资金分配与幂等", "支付终态和对账证据"],
    excludes: ["保费应收账务", "业务合同状态", "渠道资金清算规则"],
  },
  {
    id: "policy",
    repo: "titanium-policy",
    githubRepo: "titanium",
    name: "保单域",
    group: "产品与承保",
    domainType: "核心域",
    summary: "作为保险合同核心，管理投保、承保、签发、生效、失效和终止等全生命周期。",
    models: "Proposal、Insurance、Policy",
    upstream: "Customer、Product、Clause、Underwriting",
    downstream: "Billing、Payment、Maintenance、Claim",
    port: "8080",
    contextPath: "/",
    owns: ["投保资料和合同快照", "承保、签发及生效状态机", "保单版本和批单引用", "合同参与人、保障和期限事实"],
    excludes: ["产品定义与定价", "客户主数据", "核保决策过程", "收付款执行"],
  },
  {
    id: "product",
    repo: "titanium-product",
    githubRepo: "titanium-product",
    name: "产品域",
    group: "产品与承保",
    domainType: "核心域",
    summary: "定义保险产品、计划、责任组合、销售配置和版本化定价能力。",
    models: "Product、ProductPlan、PricingModel",
    upstream: "Clause、Channel、Rule Engine",
    downstream: "Policy、Billing、Maintenance",
    port: "8082",
    contextPath: "/",
    owns: ["产品与计划生命周期", "责任、条款和销售配置组合", "版本化试算与确认计算", "费用、税费和佣金计算证据"],
    excludes: ["保单合同实例", "账单与应收", "支付执行"],
  },
  {
    id: "regulatory",
    repo: "titanium-regulatory",
    githubRepo: "titanium-regulatory",
    name: "监管域",
    group: "治理与风控",
    domainType: "支撑域",
    summary: "采集跨域监管数据，生成、校验、上报监管报表并保留审计轨迹。",
    models: "RegulatoryReport、RegulatoryAudit",
    upstream: "Policy、Claim、Billing、Investment",
    downstream: "监管报送渠道",
    port: "8084",
    contextPath: "/",
    owns: ["监管口径和报表任务", "数据采集快照", "校验、上报与回执", "监管审计轨迹"],
    excludes: ["源业务数据修改", "业务交易流程", "通用操作日志"],
  },
  {
    id: "reinsurance",
    repo: "titanium-reinsurance",
    githubRepo: "titanium-reinsurance",
    name: "再保险域",
    group: "治理与风控",
    domainType: "支撑域",
    summary: "管理再保险合同、分出安排、风险累积、保费分摊及赔款摊回。",
    models: "ReinsuranceContract、Cession、Recovery",
    upstream: "Policy、Underwriting、Claim",
    downstream: "Billing、Regulatory",
    port: "8092",
    contextPath: "/",
    owns: ["再保合同与分出规则", "风险单位和累积", "分保保费与账务指令", "赔款摊回事实"],
    excludes: ["原保险合同状态", "原始理赔决定", "实际资金支付"],
  },
  {
    id: "rule-engine",
    repo: "titanium-rule-engine",
    githubRepo: "titanium-rule-engine",
    name: "规则引擎域",
    group: "治理与风控",
    domainType: "支撑域",
    summary: "管理版本化规则集、发布流程和规则执行，为各领域提供可审计的决策能力。",
    models: "RuleSet、RuleVersion、ExecutionResult",
    upstream: "Admin、Product",
    downstream: "Underwriting、Policy、Maintenance",
    port: "8090",
    contextPath: "/",
    owns: ["规则集和版本生命周期", "规则发布与回滚", "规则执行及命中证据", "租户级规则隔离"],
    excludes: ["领域业务状态变更", "工作流编排", "人工核保决定"],
  },
  {
    id: "underwriting",
    repo: "titanium-underwriting",
    githubRepo: "titanium-underwriting",
    name: "核保域",
    group: "产品与承保",
    domainType: "核心域",
    summary: "负责风险资料采集、自动规则评估、人工审核、附加条件和最终核保结论。",
    models: "UnderwritingCase、RiskAssessment、Decision",
    upstream: "Policy、Customer、Product、Rule Engine",
    downstream: "Policy、Reinsurance",
    port: "8083",
    contextPath: "/",
    owns: ["核保案件与风险快照", "自动和人工核保流程", "加费、除外及延期条件", "版本化核保决定和证据"],
    excludes: ["保单签发和生效", "产品规则定义", "客户主数据维护"],
  },
];

const supportRepositories = [
  ["运营管理前端", "titanium-admin-web", "Vue 3 管理工作台"],
  ["共享基础库", "titanium-common", "多租户、通用响应、异常与基础能力"],
  ["业务元数据", "titanium-metadata", "跨域枚举、值语义与元数据契约"],
  ["依赖基线", "titanium-parent", "Maven BOM、插件与版本治理"],
  ["构建规则", "titanium-build-tools", "架构和代码质量检查"],
  ["系统测试", "titanium-test", "跨服务集成与端到端验收"],
];

const layerDescriptions = {
  common: "通用层：模块内枚举、异常和常量",
  api: "API 层：服务间契约、Feign 接口和 DTO",
  domain: "领域层：聚合、值对象、领域事件及 Port",
  application: "应用层：用例编排、命令与查询协调",
  infrastructure: "基础设施层：Repository、远程 Adapter、消息与持久化",
  query: "查询层：CQRS 读模型与查询处理器",
  web: "Web 层：REST 入口、请求校验和响应装配",
  bootstrap: "启动层：Spring Boot 入口、配置和 Liquibase",
};

function serviceUrl(service) {
  return `${githubOrg}/${service.githubRepo}`;
}

function serviceIndex(current) {
  const groups = [...new Set(services.map((service) => service.group))];
  return groups
    .map((group) => {
      const links = services
        .filter((service) => service.group === group)
        .map((service) => {
          const label = `${service.name} \`${service.repo}\``;
          const link = `[${label}](${serviceUrl(service)})`;
          return service.id === current.id ? `**${link}**` : link;
        })
        .join(" · ");
      return `| ${group} | ${links} |`;
    })
    .join("\n");
}

function supportIndex() {
  return supportRepositories
    .map(([name, repo, purpose]) => `| [${name}](${githubOrg}/${repo}) | \`${repo}\` | ${purpose} |`)
    .join("\n");
}

function moduleRows(service) {
  const servicePath = path.join(projectRoot, service.repo);
  const prefix = `${service.repo}-`;
  return fs
    .readdirSync(servicePath, { withFileTypes: true })
    .filter((entry) => entry.isDirectory() && entry.name.startsWith(prefix))
    .map((entry) => {
      const suffix = entry.name.slice(prefix.length);
      return [suffix, `\`${entry.name}\``, layerDescriptions[suffix] ?? "服务子模块"];
    })
    .sort((left, right) => {
      const order = ["common", "api", "domain", "application", "infrastructure", "query", "web", "bootstrap"];
      return order.indexOf(left[0]) - order.indexOf(right[0]);
    })
    .map(([, name, description]) => `| ${name} | ${description} |`)
    .join("\n");
}

function bulletList(items) {
  return items.map((item) => `- ${item}`).join("\n");
}

function readme(service) {
  const designPath = path.join(projectRoot, service.repo, "DESIGN.md");
  const designLink = fs.existsSync(designPath) ? "- [详细设计](./DESIGN.md)\n" : "";
  const baseUrl = service.contextPath === "/" ? `http://localhost:${service.port}` : `http://localhost:${service.port}${service.contextPath}`;

  return `<div align="center">

# Titanium 保险核心系统

**面向多险种、全生命周期和多租户场景的 DDD + CQRS + 事件驱动保险核心平台**

[![Java](https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk&logoColor=white)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-4.0.1-6DB33F?logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Axon](https://img.shields.io/badge/Axon-4.10-4B32C3)](https://www.axoniq.io/)
[![Kafka](https://img.shields.io/badge/Kafka-4.0-231F20?logo=apachekafka&logoColor=white)](https://kafka.apache.org/)
[![License](https://img.shields.io/badge/license-project_policy-lightgrey)](${githubOrg}/titanium)

[系统全景](#titanium-是什么) · [服务索引](#微服务索引) · [架构边界](#架构与领域边界) · [当前服务](#${service.repo}) · [快速开始](#快速开始)

</div>

## 微服务索引

> 下面全部使用 GitHub 绝对链接。从任何 Titanium 仓库进入，都可以直接跳转到目标服务。

| 业务分组 | 服务 |
|---|---|
${serviceIndex(service)}

<details>
<summary><strong>共享组件、前端与工程仓库</strong></summary>

| 类型 | 仓库 | 用途 |
|---|---|---|
${supportIndex()}

</details>

## Titanium 是什么

Titanium 是保险核心业务平台，围绕保险产品从定义、投保、核保、签发、收费，到保全、理赔、再保和监管的完整生命周期建设。系统以限界上下文拆分业务能力，让每个服务拥有自己的领域模型、数据和发布节奏，并通过稳定 API 与领域事件协作。

### 设计目标

- **全险种**：支持车险、寿险、健康险、宠物险，以及投连险、万能险等账户型产品。
- **全生命周期**：覆盖产品、销售、承保、收费、保全、理赔、投资、再保和监管链路。
- **多租户**：请求、命令、事件、读模型和持久化数据均携带租户上下文。
- **可演进**：服务内部坚持 DDD 分层，服务之间通过契约和事件解耦。
- **可审计**：关键业务决定保存版本、输入摘要、业务证据与操作轨迹。

## 技术栈

| 领域 | 技术 | 用途 |
|---|---|---|
| 语言与构建 | Java 21、Maven | Record、虚拟线程、统一依赖与构建生命周期 |
| 应用框架 | Spring Boot 4.0.1、Spring Cloud OpenFeign | Web 应用、依赖注入、服务间同步契约 |
| 领域与消息 | Axon Framework 4.10、Apache Kafka 4.0 | CQRS、领域事件、异步跨域协作 |
| 数据与缓存 | MySQL 8、Redis 7.2 | 事务数据、读模型、缓存与幂等辅助 |
| 数据迁移 | Liquibase 4.26 | 数据库结构版本化 |
| 工程效率 | Lombok、MapStruct | 构造注入、日志、跨层对象映射 |
| 交付运行 | Docker、Docker Compose | 本地依赖、集成环境和容器化运行 |

> 各服务按自身边界选择依赖；例如后台 CRUD 服务不强制使用 Axon，纯共享组件也不会引入 Web 运行时。

## 架构与领域边界

### 服务内部：DDD + 六边形分层

\`\`\`mermaid
flowchart TB
    WEB[Web<br/>REST / Validation] --> APP[Application<br/>Use Case Orchestration]
    API[API<br/>Feign Contract / DTO] --> APP
    APP --> DOMAIN[Domain<br/>Aggregate / Value Object / Event]
    APP --> PORT[Domain Port]
    INFRA[Infrastructure Adapter] -. implements .-> PORT
    INFRA --> DB[(MySQL / Redis)]
    INFRA --> MQ[(Kafka / External Service)]
    EVENT[Domain Event] --> QUERY[Query Projection]
    QUERY --> READ[(Read Model)]
\`\`\`

- Web 只处理协议、鉴权、校验和响应；Application 只编排用例。
- 业务不变量进入聚合根或纯领域服务，Domain 不依赖 Spring 基础设施。
- 远程调用和消息发送由 Domain 定义 Port，Infrastructure 提供 Adapter。
- 写侧发布事实，Query 维护读模型；跨域不共享数据库表和内部实体。

### 服务之间：事件驱动协作

\`\`\`mermaid
flowchart LR
    Customer -->|CustomerCreated| Policy
    Product -->|ProductPublished| Policy
    Policy -->|ProposalSubmitted| Underwriting
    Underwriting -->|DecisionMade| Policy
    Policy -->|PolicyUnderwritten| Billing
    Billing -->|BillGenerated| Payment
    Payment -->|PaymentSucceeded| Policy
    Policy -->|PolicyActivated| Maintenance
    Policy -->|ClaimRequested| Claim
    Claim -->|CompensationApproved| Payment
    Policy --> Reinsurance
    Claim --> Regulatory
\`\`\`

### 边界规则

1. 聚合只能在所属服务内修改；其他服务通过 API 查询或以命令/事件发起协作。
2. 事件描述已经发生的业务事实，必须带有 \`tenantId\`、业务标识和必要快照，避免消费者反查写库。
3. 同步调用用于必须即时获得的判定；跨生命周期状态推进优先使用事件并保证幂等。
4. \`titanium-metadata\` 只承载稳定的跨域语义；服务专属枚举和值对象留在本域。
5. 仓储和远程 Port 由 Domain 定义，Adapter 位于 Infrastructure；Domain Service 不依赖任何 Port。

---

## ${service.repo}

> **${service.name}**：${service.summary}

| 属性 | 内容 |
|---|---|
| 限界上下文 | ${service.name}（${service.domainType}） |
| 核心模型 | ${service.models} |
| 主要上游 | ${service.upstream} |
| 主要下游 | ${service.downstream} |
| 默认地址 | [\`${baseUrl}\`](${baseUrl}) |
| GitHub | [\`${service.githubRepo}\`](${serviceUrl(service)}) |

### 能力与边界

| 本服务负责 | 本服务不负责 |
|---|---|
| ${service.owns.join("<br/>")} | ${service.excludes.join("<br/>")} |

### 核心能力

${bulletList(service.owns)}

### 协作关系

\`\`\`mermaid
flowchart LR
    UP[${service.upstream}] -->|API / Event| CURRENT[${service.name}]
    CURRENT -->|API / Event| DOWN[${service.downstream}]
\`\`\`

跨域调用必须透传 \`X-Tenant-Id\`；命令、事件和持久化模型必须保留 \`tenantId\`。服务间只依赖 \`api\` 契约或公开事件，不依赖对方的 Domain、Infrastructure 或数据库。

### 模块结构

| 模块 | 职责 |
|---|---|
${moduleRows(service)}

## 快速开始

### 环境要求

- JDK 21
- Maven 3.9+
- MySQL 8.0+
- Redis 7.2+、Kafka 4.0（按本服务配置启用）

### 构建与测试

\`\`\`bash
git clone ${serviceUrl(service)}.git
cd ${service.githubRepo}
mvn clean verify
\`\`\`

### 本地启动

\`\`\`bash
mvn -pl ${service.repo}-bootstrap -am spring-boot:run
\`\`\`

默认访问地址为 \`${baseUrl}\`。数据库、Redis、Kafka、下游服务地址及环境变量以 \`${service.repo}-bootstrap/src/main/resources/application.yml\` 为准。

## 接口与开发约定

- 面向前端的接口放在 \`web\`，服务间接口和 DTO 放在 \`api\`。
- Controller 使用 \`@Validated\` 与 JSR-303；Application 采用构造器注入。
- 跨层转换使用 MapStruct，不直接暴露持久化对象。
- 日志使用 SLF4J 占位符，不记录身份证件、Token 等敏感数据。
- 新增业务行为时优先补充聚合测试；跨域流程补充集成或契约测试。

## 相关资料

${designLink}- [Titanium 主仓库](${githubOrg}/titanium)
- [全部服务与组件](${githubOrg}?tab=repositories&q=titanium)
- [Axon Framework 文档](https://docs.axoniq.io/axon-framework-reference/4.10/)
- [Spring Boot 文档](https://docs.spring.io/spring-boot/)

---

<div align="center">

**Titanium Insurance Core** · Domain-driven, event-aware, tenant-safe.

[返回顶部](#titanium-保险核心系统) · [切换服务](#微服务索引)

</div>
`;
}

const checkOnly = process.argv.includes("--check");
let hasError = false;

for (const service of services) {
  const readmePath = path.join(projectRoot, service.repo, "README.md");
  const expected = readme(service);

  if (checkOnly) {
    const actual = fs.existsSync(readmePath) ? fs.readFileSync(readmePath, "utf8") : "";
    const lineCount = actual.split("\n").length;
    const indexSection = actual.match(/## 微服务索引\n\n[\s\S]*?\n\n<details>/)?.[0] ?? "";
    const missingLinks = services.filter((item) => !indexSection.includes(`](${serviceUrl(item)})`));
    const issues = [];
    if (actual !== expected) issues.push("内容与生成器不一致");
    if (lineCount > 300) issues.push(`超过 300 行（${lineCount}）`);
    if (missingLinks.length > 0) issues.push(`缺少服务链接：${missingLinks.map((item) => item.repo).join(", ")}`);
    if ((actual.match(/^# /gm) ?? []).length !== 1) issues.push("一级标题数量不是 1");
    if ((actual.match(/^```/gm) ?? []).length % 2 !== 0) issues.push("代码围栏未闭合");
    if (actual.includes("/Users/") || /[A-Z]:\\/.test(actual)) issues.push("包含本机绝对路径");

    if (issues.length > 0) {
      hasError = true;
      console.error(`${service.repo}/README.md: ${issues.join("；")}`);
    } else {
      console.log(`${service.repo}/README.md: OK (${lineCount} lines)`);
    }
    continue;
  }

  fs.writeFileSync(readmePath, expected, "utf8");
  const lineCount = expected.split("\n").length;
  console.log(`${service.repo}/README.md (${lineCount} lines)`);
}

if (hasError) process.exitCode = 1;
