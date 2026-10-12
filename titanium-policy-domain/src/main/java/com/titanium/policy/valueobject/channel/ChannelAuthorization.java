package com.titanium.policy.valueobject.channel;

/**
 * 渠道授权校验结论（出单准入值对象，G13/g13-08）
 * <p>
 * 承载 {@link com.titanium.policy.port.channel.ChannelAuthorizationPort} 的校验答复，回答出单链路
 * 发命令前的一个问题：「这个渠道现在能不能卖这个产品」。三个布尔位各表一层事实，
 * 判定规则内聚在 {@link #authorized()}——调用方（出单校验器）据此选择错误码，不再自行组合条件。
 * </p>
 * <p>
 * 🔴 <b>刻意不携带渠道原始状态枚举</b>：对端状态（如 {@code CommonStatus}）是渠道域的内部词汇，
 * 本域只消费「是否可用于出单」这一派生结论，状态到布尔的翻译在基础设施适配器完成（防腐层）。
 * </p>
 *
 * @param channelId         渠道ID（回显入参，用于错误提示定位）
 * @param productId         产品ID（回显入参）
 * @param channelExists     渠道是否存在
 * @param channelActive     渠道是否处于可销售状态（ACTIVE）
 * @param productAuthorized 渠道是否已授权销售该产品
 */
public record ChannelAuthorization(
        String channelId,
        String productId,
        boolean channelExists,
        boolean channelActive,
        boolean productAuthorized) {

    /**
     * 授权校验总结论：渠道存在 ∧ 状态可用 ∧ 产品已授权。
     *
     * @return 三项全部满足返回 {@code true}
     */
    public boolean authorized() {
        return channelExists && channelActive && productAuthorized;
    }
}
