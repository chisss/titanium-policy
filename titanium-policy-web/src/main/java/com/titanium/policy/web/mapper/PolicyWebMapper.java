package com.titanium.policy.web.mapper;

import java.math.BigDecimal;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.titanium.metadata.valueobject.Money;
import com.titanium.policy.api.dto.AmountDTO;
import com.titanium.policy.api.dto.CreatePolicyDTO;
import com.titanium.policy.api.dto.PolicyDTO;
import com.titanium.policy.api.dto.PolicyStatusDTO;
import com.titanium.policy.command.CreatePolicyCommand;
import com.titanium.policy.command.CreatePolicyDirectlyCommand;
import com.titanium.policy.query.result.PolicyQueryResult;
import com.titanium.policy.web.request.CreatePolicyRequest;
import com.titanium.policy.web.response.PolicyDetailVO;

/**
 * 保单 Web 层对象映射器（MapStruct）
 * <p>
 * 把边界输入翻译成 CQRS 命令/查询的转换枢纽：HTTP {@code Request} → 领域命令 {@code CreatePolicyCommand}
 * （Controller 用）、远程 {@code DTO} → 领域命令（Provider 用）、读模型结果 → 展示 {@code VO}（Controller 用）、
 * 读模型结果 → 对外 {@code DTO}（Provider 用）。application 门面入参即领域命令，本映射器在 web 层完成
 * Request/DTO → Command 的结构翻译（{@code BigDecimal}+币种 → {@code Money}）。业务默认值仅作兜底，
 * 命令的业务完整性由聚合根保证。
 * </p>
 */
@Mapper(componentModel = "spring")
public interface PolicyWebMapper {

    /**
     * HTTP Request → 领域命令（Controller 用）
     * <p>
     * 租户ID 以请求头透传值为准；金额+币种组装为 {@code Money}。DTO 未承载的领域字段留空由聚合根兜底。
     * </p>
     *
     * @param request 创建保单请求
     * @param tenantId 租户ID（请求头）
     * @return 创建保单命令
     */
    @Mapping(target = "sumInsured", expression = "java(toMoney(request.getSumInsured(), request.getCurrency()))")
    @Mapping(target = "premium", expression = "java(toMoney(request.getPremium(), request.getCurrency()))")
    @Mapping(target = "tenantId", source = "tenantId")
    CreatePolicyCommand toCommand(CreatePolicyRequest request, String tenantId);

    /**
     * HTTP Request → 一步出单命令（Controller 的 direct 端点用）
     *
     * @param request 创建保单请求
     * @param tenantId 租户ID（请求头）
     * @return 一步出单命令
     */
    @Mapping(target = "productCode", source = "request.productId")
    @Mapping(target = "totalPremium", expression = "java(toMoney(request.getPremium(), request.getCurrency()))")
    @Mapping(target = "sumInsured", expression = "java(toMoney(request.getSumInsured(), request.getCurrency()))")
    @Mapping(target = "insurancePeriodStart", source = "request.startDate")
    @Mapping(target = "insurancePeriodEnd", source = "request.endDate")
    @Mapping(target = "tenantId", source = "tenantId")
    @Mapping(target = "insuredCount", ignore = true)
    CreatePolicyDirectlyCommand toDirectCommand(CreatePolicyRequest request, String tenantId);

    /**
     * 远程 DTO → 领域命令（Provider 用）
     * <p>
     * DTO 的嵌套金额 {@code premium} 拆解为 {@code Money}；DTO 未承载的字段（投保单/形态/机构/被保险人/保额/渠道）留空。
     * </p>
     *
     * @param dto 创建保单 DTO
     * @param tenantId 租户ID（请求头）
     * @return 创建保单命令
     */
    @Mapping(target = "policyNo", source = "dto.policyNumber")
    @Mapping(target = "policyHolderId", source = "dto.customerId")
    @Mapping(target = "premium", expression = "java(toMoney(amountValue(dto.getPremium()), amountCurrency(dto.getPremium())))")
    @Mapping(target = "startDate", source = "dto.effectiveDate")
    @Mapping(target = "endDate", source = "dto.expiryDate")
    @Mapping(target = "tenantId", source = "tenantId")
    @Mapping(target = "insuranceId", ignore = true)
    @Mapping(target = "policyForm", ignore = true)
    @Mapping(target = "productId", ignore = true)
    @Mapping(target = "issueOrg", ignore = true)
    @Mapping(target = "insuredId", ignore = true)
    @Mapping(target = "sumInsured", ignore = true)
    @Mapping(target = "channel", ignore = true)
    CreatePolicyCommand toCommand(CreatePolicyDTO dto, String tenantId);

    /**
     * 远程 DTO → 一步出单命令（Provider 的 direct 端点用）
     * <p>
     * 一步出单不经投保单，直接创建并具备签发条件；DTO 未承载的领域字段留空由聚合根兜底。
     * </p>
     *
     * @param dto 创建保单 DTO
     * @param tenantId 租户ID（请求头）
     * @return 一步出单命令
     */
    @Mapping(target = "policyNo", source = "dto.policyNumber")
    @Mapping(target = "policyHolderId", source = "dto.customerId")
    @Mapping(target = "totalPremium", expression = "java(toMoney(amountValue(dto.getPremium()), amountCurrency(dto.getPremium())))")
    @Mapping(target = "insurancePeriodStart", source = "dto.effectiveDate")
    @Mapping(target = "insurancePeriodEnd", source = "dto.expiryDate")
    @Mapping(target = "tenantId", source = "tenantId")
    @Mapping(target = "productCode", ignore = true)
    @Mapping(target = "policyForm", ignore = true)
    @Mapping(target = "insuredCount", ignore = true)
    @Mapping(target = "sumInsured", ignore = true)
    @Mapping(target = "channel", ignore = true)
    CreatePolicyDirectlyCommand toDirectCommand(CreatePolicyDTO dto, String tenantId);

    /**
     * 读模型结果 → 展示 VO（Controller 用）
     *
     * @param result 读模型查询结果
     * @return 保单详情 VO
     */
    PolicyDetailVO toVO(PolicyQueryResult result);

    /**
     * 读模型结果 → 对外 DTO（Provider 用）
     *
     * @param result 读模型查询结果
     * @return 保单 DTO
     */
    @Mapping(target = "customerId", source = "policyHolderId")
    @Mapping(target = "productId", source = "productCode")
    @Mapping(target = "premium", expression = "java(toAmount(result.getPremium(), result.getCurrency() != null ? result.getCurrency().name() : null))")
    @Mapping(target = "createdAt", source = "createTime")
    @Mapping(target = "updatedAt", source = "updateTime")
    @Mapping(target = "policyItems", ignore = true)
    PolicyDTO toDTO(PolicyQueryResult result);

    /**
     * 读模型结果 → 保单状态 DTO（Provider 用）
     *
     * @param result 读模型查询结果
     * @return 保单状态 DTO
     */
    @Mapping(target = "status", expression = "java(result.getStatus() != null ? result.getStatus().name() : null)")
    PolicyStatusDTO toStatusDTO(PolicyQueryResult result);

    /**
     * BigDecimal + 币种 → Money 值对象（空安全，缺省币种 CNY）
     */
    default Money toMoney(BigDecimal value, String currency) {
        return value != null ? Money.of(value, currency != null ? currency : "CNY") : null;
    }

    /**
     * 取金额 DTO 的数值（空安全）
     */
    default BigDecimal amountValue(AmountDTO amount) {
        return amount != null ? amount.getValue() : null;
    }

    /**
     * 取金额 DTO 的币种（空安全）
     */
    default String amountCurrency(AmountDTO amount) {
        return amount != null ? amount.getCurrency() : null;
    }

    /**
     * 数值 + 币种 → 金额 DTO（空安全）
     */
    default AmountDTO toAmount(Double value, String currency) {
        if (value == null) {
            return null;
        }
        AmountDTO amount = new AmountDTO();
        amount.setValue(BigDecimal.valueOf(value));
        amount.setCurrency(currency);
        return amount;
    }
}
