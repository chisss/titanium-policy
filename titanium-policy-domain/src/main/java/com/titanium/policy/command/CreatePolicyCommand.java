package com.titanium.policy.command;

import java.time.LocalDateTime;

import com.titanium.metadata.enums.policy.PolicyForm;
import com.titanium.metadata.enums.product.ProductEnum.SalesChannel;
import com.titanium.policy.valueobject.Amount;

/*
 * 创建保单命令
 * <p>
 * 用于创建正式保单
 * </p>
 */
public record CreatePolicyCommand(
                                  /*
                                   * 保单ID
                                   */
                                  String policyId,
                                  /*
                                   * 保单号
                                   */
                                  String policyNo,
                                  /*
                                   * 关联投保单ID
                                   */
                                  String insuranceId,
                                  /*
                                   * 保单形态
                                   */
                                  PolicyForm policyForm,
                                  /*
                                   * 签发机构
                                   */
                                  String issueOrg,
                                  /*
                                   * 投保人ID
                                   */
                                  String policyHolderId,
                                  /*
                                   * 被保险人ID
                                   */
                                  String insuredId,
                                  /*
                                   * 保额
                                   */
                                  Amount sumInsured,
                                  /*
                                   * 保费
                                   */
                                  Amount premium,
                                  /*
                                   * 生效日期
                                   */
                                  LocalDateTime startDate,
                                  /*
                                   * 终止日期
                                   */
                                  LocalDateTime endDate,

                                  /*
                                   * 渠道
                                   */
                                  SalesChannel channel,

                                  /*
                                   * 租户ID
                                   */
                                  String tenantId) {
}
