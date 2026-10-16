package com.titanium.policy.application.orchestration.issuance.validator;

import org.springframework.stereotype.Component;

import com.titanium.metadata.errorcode.PolicyErrorCode;
import com.titanium.metadata.valueobject.RuleDecision;
import com.titanium.policy.exception.RemoteDependencyUnavailableException;
import com.titanium.policy.port.channel.ChannelAuthorizationPort;
import com.titanium.policy.valueobject.channel.ChannelAuthorization;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 出单渠道授权校验器（G13/g13-08）。
 * <p>
 * 出单发命令前经 {@link ChannelAuthorizationPort} 校验「渠道存在 ∧ 状态可用 ∧ 已授权该产品」，
 * 把校验结论翻译为 {@link RuleDecision}（三要素各配独立错误码，修法互不相同），与风控步骤同族——
 * 返回裁决、不抛业务异常，由编排器统一组装 {@code IssuanceResult.rejected}。
 * </p>
 * <p>
 * 🔴 <b>R-3 处置：失败关闭（fail-closed）</b>。渠道域不可达/契约失败时**拒绝出单**并返回
 * {@code ISSUANCE_CHANNEL_AUTHORIZATION_UNAVAILABLE}，绝不降级放行——未校验的渠道等于未经验证的
 * 销售来源，放行错单的合规成本远高于一次可重试的「暂不可用」。⚠️ 本处置为技术建议落地，
 * 待业务侧就 fail-closed/fail-open 正式裁示（如改判 fail-open，仅需调整本处 catch 分支）。
 * </p>
 * <p>
 * <b>渠道非必填</b>：{@code channelId} 为空时直接放行——存量无渠道出单请求（历史数据、内部直营）
 * 不因本校验而阻断。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IssuanceChannelValidator {

    private final ChannelAuthorizationPort channelAuthorizationPort;

    /**
     * 校验渠道对出单主险产品的销售授权。
     *
     * @param channelId 渠道ID（为空时跳过校验，不阻断）
     * @param productId 出单主险产品ID
     * @param tenantId  租户ID
     * @return 校验裁决（通过 / 按具体原因拒绝）
     */
    public RuleDecision validate(String channelId, String productId, String tenantId) {
        if (channelId == null || channelId.isBlank()) {
            return RuleDecision.accepted();
        }

        ChannelAuthorization authorization;
        try {
            authorization = channelAuthorizationPort.checkAuthorization(channelId, productId, tenantId);
        } catch (RemoteDependencyUnavailableException exception) {
            // 🔴 失败关闭：依赖故障不得伪装成业务结论，也不得放行（m21-02 契约异常由适配器抛出）
            log.warn("渠道授权校验不可用（失败关闭拒绝出单）: channelId={}, productId={}, tenantId={}",
                    channelId, productId, tenantId, exception);
            return RuleDecision.rejected(PolicyErrorCode.ISSUANCE_CHANNEL_AUTHORIZATION_UNAVAILABLE);
        }

        if (!authorization.channelExists()) {
            return RuleDecision.rejected(PolicyErrorCode.ISSUANCE_CHANNEL_NOT_EXIST, channelId);
        }
        if (!authorization.channelActive()) {
            return RuleDecision.rejected(PolicyErrorCode.ISSUANCE_CHANNEL_NOT_ACTIVE, channelId);
        }
        if (!authorization.productAuthorized()) {
            return RuleDecision.rejected(PolicyErrorCode.ISSUANCE_CHANNEL_PRODUCT_NOT_AUTHORIZED, channelId, productId);
        }
        return RuleDecision.accepted();
    }
}
