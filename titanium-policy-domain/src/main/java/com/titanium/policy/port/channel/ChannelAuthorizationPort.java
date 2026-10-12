package com.titanium.policy.port.channel;

import com.titanium.policy.valueobject.channel.ChannelAuthorization;

/**
 * 渠道授权校验端口（出单准入，G13/g13-08）。
 * <p>
 * 出单链路在发命令前经该端口校验「渠道存在 ∧ 状态可用 ∧ 已授权该产品」，channel API 的 Feign 协议
 * 由基础设施适配器隔离。渠道不存在、停用、未授权都是**合法的校验结论**（经
 * {@link ChannelAuthorization} 返回），不是调用错误。
 * </p>
 * <p>
 * 🔴 <b>失败关闭（fail-closed，R-3 处置）</b>：依赖不可达/契约失败时适配器抛出
 * {@code RemoteDependencyUnavailableException}（m21-02 远程失败不折叠契约），由调用方按
 * 「校验暂不可用」拒绝出单——未校验的渠道等于未经验证的销售来源，不得放行。异常与
 * 「校验完成但未通过」在错误码上可区分：前者 {@code ISSUANCE_CHANNEL_AUTHORIZATION_UNAVAILABLE}，
 * 后者按三要素分别返回 NOT_EXIST / NOT_ACTIVE / PRODUCT_NOT_AUTHORIZED。
 * </p>
 */
public interface ChannelAuthorizationPort {

    /**
     * 校验渠道对指定产品的出单授权。
     *
     * @param channelId 渠道ID
     * @param productId 产品ID
     * @param tenantId  租户ID
     * @return 授权校验结论（渠道不存在时返回 {@code channelExists=false} 的结论而非异常）
     * @throws RemoteDependencyUnavailableException 渠道服务不可达或契约失败（失败关闭）
     */
    ChannelAuthorization checkAuthorization(String channelId, String productId, String tenantId);
}
