package com.titanium.policy.event;

import java.util.List;

import com.titanium.metadata.enums.insurance.InsuranceProductType;
import com.titanium.metadata.enums.policy.PolicyForm;
import com.titanium.metadata.valueobject.Money;
import com.titanium.policy.entity.insurance.InsuredPartyList;
import com.titanium.policy.entity.policy.PolicyProduct;
import com.titanium.policy.valueobject.PolicyNo;
import com.titanium.policy.valueobject.PolicyStatus;
import com.titanium.policy.valueobject.PremiumPlan;
import com.titanium.policy.valueobject.policy.ChannelInfo;
import com.titanium.policy.valueobject.policy.CollectionInfo;
import com.titanium.policy.valueobject.policy.PolicyPeriod;

/**
 * 保单创建事件
 * <p>
 * 承载保单的完整承保事实。相较改造前，本事件补齐了三类此前<b>被丢弃或从未落地</b>的信息：
 * </p>
 * <ol>
 *   <li><b>险种段列表</b>（{@code policyProducts}）：一单多险的载体，段内含条款/标的/责任快照。
 *       此前 {@code Policy} 的 {@code insuranceProducts}/{@code subjects} 在事件溯源时恒为空列表
 *       且无命令可填，导致保单永远查不到险种与责任。</li>
 *   <li><b>缴费与收费</b>（{@code premiumPlan}/{@code collectionInfo}）：此前 {@code premiumPlan}
 *       恒 null，致 {@code activate()} 的首期保费校验被短路（未收费也能生效）。</li>
 *   <li><b>渠道与溯源</b>（{@code channelInfo}/{@code proposalId}/{@code underwritingId}/
 *       {@code marketPackageId}）：此前命令虽带 channel 字段但发事件时被丢弃，保单查不到来源。</li>
 * </ol>
 * <p>
 * 原 {@code policyItems} 为死字段（{@code Policy} 聚合无对应属性、无消费方）已移除；原
 * {@code effectiveDate}/{@code expiryDate} 收敛入 {@link PolicyPeriod}（同时承载等待期/犹豫期）。
 * {@code productId}/{@code sumInsured} 保留为主险的读侧便捷冗余，险种真相在 {@code policyProducts}。
 * </p>
 *
 * @param policyId         保单ID
 * @param policyNo         保单号
 * @param policyForm       保单形态
 * @param productId        主险产品ID（读侧便捷冗余；完整险种信息见 policyProducts）
 * @param insuranceId      关联投保单ID（可空，一步出单无投保单）
 * @param proposalId       关联意向单ID（三步出单来源，可空）
 * @param underwritingId   关联核保单ID（可空）
 * @param marketPackageId  营销包ID（弱引用，可空）
 * @param policyPeriod     保单期间（保障期 + 等待期 + 犹豫期）
 * @param premium          保单总保费（= Σ 计入段的保费）
 * @param sumInsured       主险保额（读侧便捷冗余；各段保额见 policyProducts）
 * @param policyProducts   险种段列表（L2，含条款/标的/责任快照）
 * @param premiumPlan      缴费计划
 * @param collectionInfo   收费信息
 * @param channelInfo      渠道信息
 * @param status           保单初始状态
 * @param insuredPartyList 参与方清单（投保人/被保险人/受益人）
 * @param insuranceType    主险险种三级分类（读侧便捷冗余）
 * @param tenantId         租户ID
 */
public record PolicyCreatedEvent(String policyId, PolicyNo policyNo, PolicyForm policyForm, String productId,
                                 String insuranceId, String proposalId, String underwritingId, String marketPackageId,
                                 PolicyPeriod policyPeriod, Money premium, Money sumInsured,
                                 List<PolicyProduct> policyProducts, PremiumPlan premiumPlan,
                                 CollectionInfo collectionInfo, ChannelInfo channelInfo, PolicyStatus status,
                                 InsuredPartyList insuredPartyList, InsuranceProductType insuranceType,
                                 String tenantId) {
}
