package com.titanium.policy.query.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

import com.titanium.policy.event.insurance.InsuranceCreatedEvent;
import com.titanium.policy.query.view.InsuranceView;

/**
 * 投保单读模型投影映射器（MapStruct，事件 → 读模型字段拷贝）
 * <p>
 * 承接投保单"新建型"投影（{@link InsuranceCreatedEvent} → {@link InsuranceView}）的字段映射，取代投影处理器
 * 中逐字段 set。采用 {@link MappingTarget} 就地更新既有/新建 View 实例，保留投影的 upsert 语义；
 * {@link NullValuePropertyMappingStrategy#IGNORE} 确保事件缺省字段不覆盖 View 既有值。
 * </p>
 * <p>
 * <b>职责边界</b>：仅做纯字段结构翻译。以下字段由投影处理器控制、映射器 {@code ignore}：审计时间戳
 * （createTime 仅首次、updateTime 每次 now）——注意事件同名 {@code createTime} 字段亦须 ignore 以免
 * 覆盖投影时间；投保单初始状态（DRAFT，属创建期语义，处理器显式赋值）。
 * </p>
 */
@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
        unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface InsuranceViewMapper {

    /**
     * 投保单创建事件 → 投保单读模型（就地 upsert）。
     * <p>
     * 事件与读模型同名字段（insuranceId/insuranceNo/proposalId/policyForm/holderId/insuredCount/
     * exactPremium/insurancePeriodStart/insurancePeriodEnd/insuranceType/tenantId）自动映射；状态由处理器
     * 置 DRAFT，审计时间戳由处理器 stamp，均 {@code ignore}。
     * </p>
     */
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    void applyCreated(@MappingTarget InsuranceView view, InsuranceCreatedEvent event);
}
