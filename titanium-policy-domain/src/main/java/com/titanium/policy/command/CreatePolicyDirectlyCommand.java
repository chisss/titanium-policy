package com.titanium.policy.command;

import java.util.List;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

import com.titanium.metadata.enums.insurance.InsuranceProductType;
import com.titanium.metadata.enums.policy.PolicyForm;
import com.titanium.metadata.valueobject.Money;
import com.titanium.policy.entity.insurance.InsuredPartyList;
import com.titanium.policy.entity.policy.PolicyProduct;
import com.titanium.policy.valueobject.PremiumPlan;
import com.titanium.policy.valueobject.policy.ChannelInfo;
import com.titanium.policy.valueobject.policy.CollectionInfo;
import com.titanium.policy.valueobject.policy.PolicyPeriod;

/**
 * 一步出单直接创建保单命令（不经过意向单与投保单）
 * <p>
 * 适用于免核保的短险（交强险、短期意外险）：录入即出单。
 * </p>
 * <p>
 * 🔴 <b>补齐 {@code insuredPartyList}</b>：改造前本命令<b>无参与方字段</b>，聚合在
 * {@code Policy(CreatePolicyDirectlyCommand)} 中向事件传 null，导致一步出单产出的保单
 * 查不到投保人/被保险人/受益人。同时补齐险种段、保单期间、缴费与收费信息，使一步出单与
 * 两步/三步出单产出的保单结构完全一致（下游读侧与理赔无需区分出单模式）。
 * </p>
 * <p>
 * 原 {@code productCode}/{@code insuredCount} 移除：前者在险种段快照内、后者可由参与方清单推导。
 * 原 {@code insurancePeriodStart}/{@code End} 收敛入 {@link PolicyPeriod}；原 {@code channel}
 * 收敛入 {@link ChannelInfo}。
 * </p>
 *
 * @param policyId         保单ID
 * @param policyNo         保单号
 * @param marketPackageId  营销包ID（弱引用，可空）
 * @param policyForm       保单形态
 * @param productId        主险产品ID（读侧便捷冗余）
 * @param insuredPartyList 参与方清单（投保人/被保险人/受益人）
 * @param policyProducts   险种段列表（L2，1..N，含条款/标的/责任快照）
 * @param sumInsured       主险保额（读侧便捷冗余）
 * @param totalPremium     保单总保费（= Σ 计入段的保费，聚合内校验守恒）
 * @param policyPeriod     保单期间（保障期 + 等待期 + 犹豫期）
 * @param premiumPlan      缴费计划
 * @param collectionInfo   收费信息
 * @param channelInfo      渠道信息
 * @param insuranceType    主险险种三级分类（可空）
 * @param tenantId         租户ID
 */
public record CreatePolicyDirectlyCommand(@TargetAggregateIdentifier String policyId, String policyNo,
                                          String bizNo, String marketPackageId, PolicyForm policyForm, String productId,
                                          InsuredPartyList insuredPartyList, List<PolicyProduct> policyProducts,
                                          Money sumInsured, Money totalPremium, PolicyPeriod policyPeriod,
                                          PremiumPlan premiumPlan, CollectionInfo collectionInfo,
                                          ChannelInfo channelInfo, InsuranceProductType insuranceType,
                                          String tenantId) {

    /**
     * 兼容旧的一步出单调用方。
     */
    public CreatePolicyDirectlyCommand(String policyId, String policyNo, String marketPackageId, PolicyForm policyForm,
                                       String productId, InsuredPartyList insuredPartyList,
                                       List<PolicyProduct> policyProducts, Money sumInsured, Money totalPremium,
                                       PolicyPeriod policyPeriod, PremiumPlan premiumPlan,
                                       CollectionInfo collectionInfo, ChannelInfo channelInfo,
                                       InsuranceProductType insuranceType, String tenantId) {
        this(policyId, policyNo, null, marketPackageId, policyForm, productId, insuredPartyList, policyProducts,
                sumInsured, totalPremium, policyPeriod, premiumPlan, collectionInfo, channelInfo, insuranceType,
                tenantId);
    }
}
