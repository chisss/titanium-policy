package com.titanium.policy.valueobject.policy;

import com.titanium.metadata.enums.product.ProductEnum.SalesChannel;

/**
 * 渠道信息值对象
 * <p>
 * 记录保单的销售来源。此前 {@code CreatePolicyCommand.channel} 字段虽存在但<b>发事件时被丢弃</b>，
 * {@code PolicyCreatedEvent} 与 {@code Policy} 聚合均无渠道字段（仅 {@code Proposal} 有），
 * 导致保单查不到来源渠道。本值对象补齐该链路。
 * </p>
 * <p>
 * {@code channelId} 指向 channel 域渠道主数据；{@code salesChannel} 是渠道大类枚举
 * （代理人/银保/线上/经纪/电销/团险直销）。渠道授权校验、佣金计算属营销专项（MKT-SP），
 * 本期仅落库溯源。
 * </p>
 *
 * @param channelId    渠道ID（指向 channel 域）
 * @param channelCode  渠道编码（快照）
 * @param salesChannel 销售渠道大类
 * @param agentId      代理人/业务员ID（占位，MKT-SP 细化佣金归属）
 * @param brokerId     经纪机构ID（占位，MKT-SP 细化）
 */
public record ChannelInfo(String channelId, String channelCode, SalesChannel salesChannel, String agentId,
                          String brokerId) {

    /**
     * 构建仅含渠道大类的渠道信息（无渠道主数据关联的直销场景）。
     *
     * @param salesChannel 销售渠道大类
     * @return 渠道信息
     */
    public static ChannelInfo ofSalesChannel(SalesChannel salesChannel) {
        return new ChannelInfo(null, null, salesChannel, null, null);
    }

    /**
     * 是否关联了 channel 域的渠道主数据。
     *
     * @return 已关联返回 {@code true}
     */
    public boolean hasChannelReference() {
        return channelId != null && !channelId.isBlank();
    }
}
