package com.titanium.policy.port.product;

import java.math.BigDecimal;
import java.util.List;

import com.titanium.metadata.enums.product.ProductEnum.IssuanceMode;
import com.titanium.policy.valueobject.product.ProductBasicInfo;
import com.titanium.policy.valueobject.product.ProductClauseRef;
import com.titanium.policy.valueobject.product.ProductIssueRules;
import com.titanium.policy.valueobject.product.ProductSumInsuredTiers;

/**
 * 产品服务端口（driven port，与聚合平级）
 * <p>
 * 出单链路经此端口向产品域取「产品是怎么配置的」：出单模式（决定走几步）、投保条件（校验年龄/保额/
 * 职业）、保障期间与缴费选项、保单形态、核保模式、条款绑定关系。产品配置是出单流程的<b>驱动源</b>——
 * 调用方不硬编码出单步数与业务规则。
 * </p>
 * <p>
 * 🔴 <b>强类型化</b>：改造前 5 个方法中 4 个返回 {@code Object}，调用方需 {@code JSON.parseObject}
 * 手工拆包，违反「Feign 返回类型必须强类型、禁 Map/Object」红线。现出参为
 * {@code valueobject/product} 包下的防腐值对象，产品域 DTO 细节封闭在 Adapter 内。
 * </p>
 * <p>
 * 出参值对象<b>不内嵌于本接口</b>：它们同时是领域服务
 * （{@code IssuanceEligibilityDomainService}）的入参，内嵌会让领域服务被迫 import Port 包，
 * 触发 ArchUnit「领域服务不得依赖 Port」断言。详见 {@link ProductIssueRules} 的包位置说明。
 * </p>
 * <p>
 * 🔴 <b>本端口即 policy 与 product 的全部协作面，不订阅 product 主题（2026-09-14 m6-907 判定）</b>：
 * policy 对产品的取用是<b>业务时点的同步实时查询</b>（出单装配/投保受理），产品状态变更无需异步通知——
 * 在售校验由出单必经的 CONFIRM 试算同步强校验（product 侧对非 {@code EFFECTIVE} 抛
 * {@code PRICING_PLAN_NOT_EFFECTIVE} 阻断出单），已出保单为产品快照、产品下架不回溯。
 * 故 {@code titanium.product.created/audited/invalidated} 三主题<b>均不接</b>入 policy，
 * 判定依据见 {@code docs/技术文档/跨域事件目录-2026-09.md} §六.15。
 * </p>
 */
public interface ProductServicePort {

    /**
     * 取产品基本信息（编码/名称/版本/险种分类/状态）。
     *
     * @param productId 产品ID
     * @return 产品基本信息；产品不存在时返回 null
     */
    ProductBasicInfo getProductBasicInfo(String productId);

    /**
     * 取产品配置的出单模式（产品驱动出单的类型化契约）。
     * <p>
     * 取代调用方硬编码出单步数：由产品域配置决定该产品走一步/两步/三步出单。
     * </p>
     * <p>
     * 🔴 与 {@link #getIssueRules(String)} 的「未配置返回 null」不同，本方法**不返回 null**：
     * 出单模式是流程路由的必要输入，取不到就无法决定后续步骤，故产品及其模板均未配置时抛
     * {@code PolicyBusinessRuleException}（{@code PolicyErrorCode.ISSUANCE_MODE_NOT_CONFIGURED}，
     * 20007027）。调用方**不应**用 try/catch 兜底成「走默认模式」——那会让未配出单流程的产品
     * 静默按默认流程出单。
     * </p>
     *
     * @param productId 产品ID
     * @return 出单模式（非 null）
     * @throws com.titanium.policy.exception.PolicyBusinessRuleException 产品及其模板均未配置出单流程
     */
    IssuanceMode getIssuanceMode(String productId);

    /**
     * 取产品投保规则（一次取全，避免出单受理阶段多次远程调用）。
     *
     * @param productId 产品ID
     * @return 产品投保规则；产品未配置时返回 null
     */
    ProductIssueRules getIssueRules(String productId);

    /**
     * 取产品的责任级保额档次配置（G01 产出契约的消费入口，见 G02/AC-09）。
     * <p>
     * 与 {@link #getIssueRules(String)} 是<b>两个不同来源</b>：后者取产品投保条件
     * （{@code insureCondition} 的 min/max 保额），本方法取责任级档次契约
     * （档位清单 + 区间 + 是否开放自由填）。二者互补，不得相互替代。
     * </p>
     * <p>
     * 🔴 <b>三态语义（取不到 ≠ 未配置 ≠ 不接纳）</b>：产品查询成功但未配置任何责任档次时返回
     * <b>空清单的载体</b>（非 null、非异常）——「未配置」是产品域的合法中间状态；远程调用失败则抛
     * {@code RemoteDependencyUnavailableException}，<b>绝不折叠成空清单</b>（那会把「服务不可用」
     * 静默变成「该产品没配档次」从而绕过校验）。
     * </p>
     * <p>
     * ⚠️ 读自产品域读模型，<b>最终一致</b>：刚写入的档次配置可能短暂不可见（对端契约已标注）。
     * </p>
     *
     * @param productId 产品ID
     * @return 该产品全部责任的档次配置；未配置时 {@code coverages} 为空列表
     * @throws com.titanium.policy.exception.RemoteDependencyUnavailableException 远程调用失败或下游返回失败码
     */
    ProductSumInsuredTiers getCoverageSumInsuredConfigs(String productId);

    /**
     * 取产品的管理费年费率（投连/万能投资账户按月计提管理费的年化费率，G14/g14-04a）。
     * <p>
     * 来源：产品定价基础规则 {@code typeSpecificConfig} 的 {@code managementFeeRate} 键（产品域
     * {@code PricingBasicRuleResponse.managementFeeRate} 强类型暴露）。出单时由 {@code IssuanceSaga}
     * 取数并随开户命令固化到投资账户合同字段，investment 域据此逐月计提（G14/R-3）。
     * </p>
     * <p>
     * 🔴 <b>三态语义</b>：产品查询成功但未配置管理费率 ⇒ 返回 {@code null}——这是<b>合法形态</b>
     * （该产品不收取管理费，账户以「无费率」开户）；远程调用失败 ⇒ 抛
     * {@code RemoteDependencyUnavailableException}，<b>绝不折叠成 null</b>——折叠会把「服务不可用」
     * 静默固化成「永久无费率账户」，此后每月计提按「不收费」静默跳过、事后无从对账
     * （与 {@link #getCoverageSumInsuredConfigs(String)} 同一红线）。
     * </p>
     * <p>
     * ⚠️ 读自产品域读模型，<b>最终一致</b>：刚写入的费率配置可能短暂不可见。
     * </p>
     *
     * @param productId 产品ID
     * @return 管理费年费率（如 0.015）；产品未配置时返回 null（合法，不收费）
     * @throws com.titanium.policy.exception.RemoteDependencyUnavailableException 远程调用失败或下游返回失败码
     */
    BigDecimal getManagementFeeRate(String productId);

    /**
     * 取产品绑定的条款关联（条款ID + 版本 + 是否主条款）。
     * <p>
     * 出单时据此向条款域取条款与责任，装配保单的条款快照（L2.5）与责任快照（L4）。
     * </p>
     *
     * @param productId 产品ID
     * @param tenantId  租户ID
     * @return 条款关联列表；未绑定条款时返回空列表
     */
    List<ProductClauseRef> getClauseRefs(String productId, String tenantId);
}
