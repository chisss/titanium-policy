package com.titanium.policy.infrastructure.adapter.mapper;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import com.titanium.billing.api.request.ConfirmedPremiumReferenceRequest;
import com.titanium.billing.api.request.CreateBillRequest;
import com.titanium.metadata.valueobject.Money;
import com.titanium.policy.valueobject.billing.PremiumBillRequest;
import com.titanium.policy.valueobject.pricing.PremiumCalculationReference;

/**
 * 计费请求映射器（policy 域跨域出站防腐映射）
 * <p>
 * 将保单域值对象 {@link PremiumBillRequest} 映射为计费域 Feign 契约 {@link CreateBillRequest}，
 * 取代 {@code BillingServiceAdapter} 中逐字段 set。金额值对象 {@link Money} 经 {@link #moneyToAmount}
 * 拆解为 {@link BigDecimal}；计费类型固定为保费、出账日取当前日期（承保出单即开单），由 {@link Mapping}
 * 表达式声明，语义集中可见。
 * </p>
 */
@Mapper(componentModel = "spring")
public interface BillingRequestMapper {

    /**
     * 首期保费账单请求 → 创建账单 Feign 请求
     * <p>
     * billingAccountId/createdBy 保单域无对应来源，保持 null 由计费域按缺省处理；dueDate 直接使用
     * 保单域提供的首期应缴日期。
     * </p>
     */
    @Mapping(target = "billingType", constant = "PREMIUM")
    @Mapping(target = "amount", source = "premium", qualifiedByName = "moneyToAmount")
    @Mapping(target = "issueDate", expression = "java(currentIssueDate())")
    @Mapping(target = "billingAccountId", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "premiumCalculations", source = "calculationReferences")
    CreateBillRequest toCreateBillRequest(PremiumBillRequest request);

    ConfirmedPremiumReferenceRequest toConfirmedPremiumReferenceRequest(PremiumCalculationReference reference);

    /** Money 值对象 → 账单金额（空安全） */
    @Named("moneyToAmount")
    default BigDecimal moneyToAmount(Money premium) {
        return premium != null ? premium.value() : null;
    }

    /** 出账日期：承保出单即开立首期账单，取当前日期 */
    default LocalDate currentIssueDate() {
        return LocalDate.now();
    }
}
