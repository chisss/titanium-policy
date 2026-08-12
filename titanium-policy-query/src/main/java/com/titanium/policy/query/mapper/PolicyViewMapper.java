package com.titanium.policy.query.mapper;

import java.math.BigDecimal;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

import com.titanium.metadata.enums.BaseEnum;
import com.titanium.metadata.enums.CurrencyEnum;
import com.titanium.metadata.valueobject.Money;
import com.titanium.policy.event.AnnuityPayoutStartedEvent;
import com.titanium.policy.event.PolicyCreatedEvent;
import com.titanium.policy.event.PolicyEndorsedEvent;
import com.titanium.policy.query.view.AnnuityPayoutPlanView;
import com.titanium.policy.query.view.PolicyEndorsementView;
import com.titanium.policy.query.view.PolicyView;
import com.titanium.policy.valueobject.PolicyNo;

/**
 * 保单聚合族读模型投影映射器（MapStruct，事件 → 读模型字段拷贝）
 * <p>
 * 承接 Policy 聚合族三个"新建型"投影的事件 record → View 字段映射，取代投影处理器中逐字段 set：
 * 保单创建（{@link PolicyView}）、保单批改（{@link PolicyEndorsementView}）、年金给付期启动
 * （{@link AnnuityPayoutPlanView}）。采用 {@link MappingTarget} 就地更新既有/新建 View 实例，保留投影的
 * upsert 语义；{@link NullValuePropertyMappingStrategy#IGNORE} 确保事件缺省字段不覆盖 View 既有值。
 * </p>
 * <p>
 * <b>职责边界</b>：仅做纯字段/值对象结构翻译（{@link Money} 拆解、{@link PolicyNo} 取值、{@link BaseEnum}
 * 取 code）。以下含运行时副作用或创建期语义的字段仍由投影处理器控制、映射器 {@code ignore}：审计时间戳
 * （createTime 仅首次、updateTime 每次 now）、状态默认值/状态机映射（保单状态含 null 兜底 + 本地→metadata
 * 枚举映射）、给付计划初始状态（已给付 0 期 / 给付中）、批改落地时间（now 兜底）。
 * </p>
 */
@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
        unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface PolicyViewMapper {

    /**
     * 保单创建事件 → 保单读模型（就地 upsert）。
     * <p>
     * 保费值对象拆为 premium+currency（币种转 {@link CurrencyEnum}）；保单号值对象取值；起止期字段改名映射。
     * 保单状态含 null 兜底与状态机映射，由处理器控制，此处 {@code ignore}。
     * </p>
     */
    @Mapping(target = "policyNo", source = "policyNo", qualifiedByName = "policyNoValue")
    @Mapping(target = "premium", source = "premium", qualifiedByName = "moneyValue")
    @Mapping(target = "currency", source = "premium", qualifiedByName = "moneyCurrencyEnum")
    @Mapping(target = "startDate", source = "policyPeriod.insurancePeriodStart")
    @Mapping(target = "endDate", source = "policyPeriod.insurancePeriodEnd")
    @Mapping(target = "waitingPeriodEndDate", source = "policyPeriod.waitingPeriodEndDate")
    @Mapping(target = "hesitationPeriodEndDate", source = "policyPeriod.hesitationPeriodEndDate")
    @Mapping(target = "sumInsured", source = "sumInsured", qualifiedByName = "moneyValue")
    @Mapping(target = "totalPremium", source = "premium", qualifiedByName = "moneyValue")
    @Mapping(target = "collectionMode", source = "collectionInfo.collectionMode", qualifiedByName = "enumCode")
    @Mapping(target = "collectionStatus", source = "collectionInfo.collectionStatus", qualifiedByName = "enumCode")
    @Mapping(target = "collectedAmount", source = "collectionInfo.collectedAmount", qualifiedByName = "moneyValue")
    @Mapping(target = "channelId", source = "channelInfo.channelId")
    @Mapping(target = "salesChannel", source = "channelInfo.salesChannel", qualifiedByName = "enumCode")
    @Mapping(target = "agentId", source = "channelInfo.agentId")
    @Mapping(target = "premiumWaived", constant = "false")
    @Mapping(target = "policyStatus", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    void applyCreated(@MappingTarget PolicyView view, PolicyCreatedEvent event);

    /**
     * 保单批改事件 → 批单流水读模型（就地 upsert）。
     * <p>
     * 批改类型/大类枚举取 code；批改后版本号与批单生效日字段改名映射。批改落地时间（endorsedAt）含 now 兜底，
     * 由处理器控制，此处 {@code ignore}。
     * </p>
     */
    @Mapping(target = "updateType", source = "updateType", qualifiedByName = "enumCode")
    @Mapping(target = "category", source = "category", qualifiedByName = "enumCode")
    @Mapping(target = "policyVersion", source = "versionAfter")
    @Mapping(target = "effectiveDate", source = "endorsementEffectiveDate")
    @Mapping(target = "endorsedAt", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    void applyEndorsed(@MappingTarget PolicyEndorsementView view, PolicyEndorsedEvent event);

    /**
     * 年金给付期启动事件 → 年金给付计划读模型（就地 upsert）。
     * <p>
     * 主键 id 取 policyId；给付频率枚举取 code；每期金额值对象拆为 amount+currency（币种取原始 ISO 串）。
     * 初始已给付期数（0）与给付状态（给付中）属创建期语义，由处理器控制，此处 {@code ignore}。
     * </p>
     */
    @Mapping(target = "id", source = "policyId")
    @Mapping(target = "frequency", source = "frequency", qualifiedByName = "enumCode")
    @Mapping(target = "amount", source = "amountPerInstallment", qualifiedByName = "moneyValue")
    @Mapping(target = "currency", source = "amountPerInstallment", qualifiedByName = "moneyCurrencyRaw")
    @Mapping(target = "paidInstallments", ignore = true)
    @Mapping(target = "payoutStatus", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    void applyPayoutStarted(@MappingTarget AnnuityPayoutPlanView view, AnnuityPayoutStartedEvent event);

    /** 保单号值对象 → 保单号字符串（空安全） */
    @Named("policyNoValue")
    default String policyNoValue(PolicyNo policyNo) {
        return policyNo != null ? policyNo.value() : null;
    }

    /** Money → 金额数值（空安全） */
    @Named("moneyValue")
    default BigDecimal moneyValue(Money money) {
        return money != null ? money.value() : null;
    }

    /** Money → 币种枚举（空安全，用于 currency 为 {@link CurrencyEnum} 的读模型） */
    @Named("moneyCurrencyEnum")
    default CurrencyEnum moneyCurrencyEnum(Money money) {
        return money != null ? CurrencyEnum.fromCode(money.currency()) : null;
    }

    /** Money → 原始币种串（空安全，用于 currency 为 ISO 字符串的读模型） */
    @Named("moneyCurrencyRaw")
    default String moneyCurrencyRaw(Money money) {
        return money != null ? money.currency() : null;
    }

    /** 枚举 → 稳定 code 串（空安全，适用所有 {@link BaseEnum} 实现枚举） */
    @Named("enumCode")
    default String enumCode(BaseEnum value) {
        return value != null ? value.getCode() : null;
    }
}
