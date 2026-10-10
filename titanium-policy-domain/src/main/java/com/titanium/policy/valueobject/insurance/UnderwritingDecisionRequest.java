package com.titanium.policy.valueobject.insurance;

import java.math.BigDecimal;
import java.util.List;

import com.titanium.metadata.enums.customer.CustomerEnum.CustomerGender;

/**
 * 核保决策请求值对象
 * <p>
 * 由投保出单 Saga 在投保单提交核保后构建，作为调用核保域获取核保结论的入参。
 * 字段来源于 {@code InsuranceSubmittedForUnderwritingEvent} 及 Saga 内存储的参与方清单，
 * 不含跨域技术细节，保证领域层与核保服务的具体通信方式（Feign/Kafka）解耦。
 * </p>
 * <p>
 * UW-2 新增被保人核保要素字段（年龄/性别/职业类别/BMI），字段均为可选（null 表示未提供）。
 * </p>
 * <p>
 * <b>G02/AC-01 订正</b>：原注「当前核保 API 仅支持职业类别与 BMI；年龄、性别先保留在保单域端口契约中，
 * 待跨域 API 明确扩展后再透传」<b>已失效</b>——核保域 {@code SubmitUnderwritingInputApiRequest}
 * 现已补齐 {@code age}/{@code gender} 两个字段，四要素经
 * {@code SyncUnderwritingDecisionAdapter} 全量透传。
 * </p>
 * <p>
 * 🔴 <b>但承保前置链路实际只提供得出前两项</b>：年龄/性别来自参与方清单
 * {@code InsuredPartyList.InsuredInfo}，而该实体<b>无</b>职业类别与 BMI 字段——二者属柜面/APP
 * 完整录入才有的人力要素，出单主链路取不到。故后两项在本链路恒为 {@code null}，
 * 由 Saga 显式告警留痕（非静默），需完整要素的险种应走人工核保或补充录入通道。
 * </p>
 *
 * @param insuranceId              投保单ID（核保关联键）
 * @param holderId                 投保人ID
 * @param insuredCount             被保人数量
 * @param sumInsured               真实保额
 * @param premium                  应缴保费
 * @param currency                 币种
 * @param productCodes             投保险种编码列表
 * @param tenantId                 租户ID
 * @param primaryInsuredAge        首要被保人年龄（可为 null）
 * @param primaryInsuredGender     首要被保人性别（可为 null）
 * @param primaryInsuredOccupationCategory 首要被保人职业类别（1-6，可为 null）
 * @param primaryInsuredBmi        首要被保人 BMI（可为 null）
 */
public record UnderwritingDecisionRequest(String insuranceId, String holderId, int insuredCount,
                                          BigDecimal sumInsured, BigDecimal premium, String currency,
                                          List<String> productCodes, String tenantId, Integer primaryInsuredAge,
                                          CustomerGender primaryInsuredGender, Integer primaryInsuredOccupationCategory,
                                          BigDecimal primaryInsuredBmi) {
}
