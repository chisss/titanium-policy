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
 * UW-2 新增被保人核保要素字段（年龄/性别/职业类别/BMI），供核保域富核保路径评估风险评分。
 * 字段均为可选（null 表示未提供），核保域在输入不足时按保守标准体兜底。
 * </p>
 *
 * @param insuranceId              投保单ID（核保关联键）
 * @param holderId                 投保人ID
 * @param insuredCount             被保人数量
 * @param premium                  应缴保费
 * @param currency                 币种
 * @param productCodes             投保险种编码列表
 * @param tenantId                 租户ID
 * @param primaryInsuredAge        首要被保人年龄（可为 null）
 * @param primaryInsuredGender     首要被保人性别（可为 null）
 * @param primaryInsuredOccupationCategory 首要被保人职业类别（1-6，可为 null）
 * @param primaryInsuredBmi        首要被保人 BMI（可为 null）
 */
public record UnderwritingDecisionRequest(String insuranceId, String holderId, int insuredCount, BigDecimal premium,
                                          String currency, List<String> productCodes, String tenantId,
                                          Integer primaryInsuredAge, CustomerGender primaryInsuredGender,
                                          Integer primaryInsuredOccupationCategory, BigDecimal primaryInsuredBmi) {
}
