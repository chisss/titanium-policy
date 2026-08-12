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
 * 创建保单命令（承保出单，由出单编排器 / 出单 Saga 发出）
 * <p>
 * 相较改造前补齐险种段（一单多险）、保单期间（含等待期/犹豫期）、缴费计划、收费信息、渠道信息
 * 与三级溯源指针（proposalId/underwritingId）。
 * </p>
 * <p>
 * 移除原 {@code policyHolderId}/{@code insuredId} 两字段：与 {@code insuredPartyList} 语义重复
 * （清单内已含投保人与被保险人及其 customerId），且此前在发事件时被直接丢弃，属无效字段。
 * 参与方统一由清单承载。原 {@code startDate}/{@code endDate} 收敛入 {@link PolicyPeriod}；
 * 原 {@code channel} 收敛入 {@link ChannelInfo}。
 * </p>
 *
 * @param policyId         保单ID
 * @param policyNo         保单号
 * @param insuranceId      关联投保单ID（一步出单为 null）
 * @param proposalId       关联意向单ID（三步出单来源，可空）
 * @param underwritingId   关联核保单ID（可空）
 * @param marketPackageId  营销包ID（弱引用，可空）
 * @param policyForm       保单形态
 * @param productId        主险产品ID（读侧便捷冗余）
 * @param issueOrg         签发机构
 * @param insuredPartyList 参与方清单（投保人/被保险人/受益人）
 * @param policyProducts   险种段列表（L2，1..N，含条款/标的/责任快照）
 * @param sumInsured       主险保额（读侧便捷冗余）
 * @param premium          保单总保费（= Σ 计入段的保费，聚合内校验守恒）
 * @param policyPeriod     保单期间（保障期 + 等待期 + 犹豫期）
 * @param premiumPlan      缴费计划
 * @param collectionInfo   收费信息
 * @param channelInfo      渠道信息
 * @param insuranceType    主险险种三级分类（可空，向后兼容存量事件）
 * @param tenantId         租户ID
 */
public record CreatePolicyCommand(@TargetAggregateIdentifier String policyId, String policyNo, String insuranceId,
                                  String proposalId, String underwritingId, String marketPackageId,
                                  PolicyForm policyForm, String productId, String issueOrg,
                                  InsuredPartyList insuredPartyList, List<PolicyProduct> policyProducts,
                                  Money sumInsured, Money premium, PolicyPeriod policyPeriod, PremiumPlan premiumPlan,
                                  CollectionInfo collectionInfo, ChannelInfo channelInfo,
                                  InsuranceProductType insuranceType, String tenantId) {
}
