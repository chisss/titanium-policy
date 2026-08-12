package com.titanium.policy.port;

import java.util.List;

import com.titanium.metadata.enums.product.ProductEnum.IssuanceMode;
import com.titanium.policy.valueobject.product.ProductBasicInfo;
import com.titanium.policy.valueobject.product.ProductClauseRef;
import com.titanium.policy.valueobject.product.ProductIssueRules;

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
 */
public interface ProductServicePort {

    /**
     * 取产品基本信息（编码/名称/版本/险种分类/状态）。
     *
     * @param productId 产品ID
     * @param tenantId  租户ID
     * @return 产品基本信息；产品不存在时返回 null
     */
    ProductBasicInfo getProductBasicInfo(String productId, String tenantId);

    /**
     * 取产品配置的出单模式（产品驱动出单的类型化契约）。
     * <p>
     * 取代调用方硬编码出单步数：由产品域配置决定该产品走一步/两步/三步出单。
     * </p>
     *
     * @param productId 产品ID
     * @param tenantId  租户ID
     * @return 出单模式
     */
    IssuanceMode getIssuanceMode(String productId, String tenantId);

    /**
     * 取产品投保规则（一次取全，避免出单受理阶段多次远程调用）。
     *
     * @param productId 产品ID
     * @param tenantId  租户ID
     * @return 产品投保规则；产品未配置时返回 null
     */
    ProductIssueRules getIssueRules(String productId, String tenantId);

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
