package com.titanium.policy.valueobject.insurance;

import java.math.BigDecimal;
import java.util.List;

/**
 * 核保决策请求值对象
 * <p>
 * 由投保出单 Saga 在投保单提交核保后构建，作为调用核保域获取核保结论的入参。
 * 字段来源于 {@code InsuranceSubmittedForUnderwritingEvent}，不含跨域技术细节，
 * 保证领域层与核保服务的具体通信方式（Feign/Kafka）解耦。
 * </p>
 *
 * @param insuranceId   投保单ID（核保关联键）
 * @param holderId      投保人ID
 * @param insuredCount  被保人数量
 * @param premium       应缴保费
 * @param currency      币种
 * @param productCodes  投保险种编码列表
 * @param tenantId      租户ID
 */
public record UnderwritingDecisionRequest(String insuranceId, String holderId, int insuredCount, BigDecimal premium,
                                          String currency, List<String> productCodes, String tenantId) {
}
