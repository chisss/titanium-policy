package com.titanium.policy.infrastructure.adapter.channel;

import org.springframework.stereotype.Component;

import com.titanium.channel.api.ChannelApi;
import com.titanium.channel.api.response.channel.ChannelAuthorizationResponse;
import com.titanium.metadata.enums.CommonStatus;
import com.titanium.metadata.errorcode.SystemErrorCode;
import com.titanium.metadata.response.ApiResponse;
import com.titanium.policy.exception.RemoteDependencyUnavailableException;
import com.titanium.policy.port.channel.ChannelAuthorizationPort;
import com.titanium.policy.valueobject.channel.ChannelAuthorization;

import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 渠道授权校验防腐适配器（G13/g13-08）。
 * <p>
 * 仅在基础设施层依赖 channel API；把对端契约响应翻译为本域值对象
 * {@link ChannelAuthorization}（状态枚举 → 布尔「是否可用于出单」，防腐层不外漏对端词汇）。
 * </p>
 * <p>
 * 🔴 <b>失败关闭（m21-02 契约）</b>：契约失败（错误信封 / 成功但无载荷 / Feign 异常）一律抛
 * {@link RemoteDependencyUnavailableException}，绝不降级为「不授权」的结论——二者在调用方
 * 的错误码不同（暂不可用 vs 未授权），把依赖故障伪装成业务结论会让排查指向错误方向
 * （条款/产品适配器同款判据）。调用方（出单校验器）据此 fail-closed 拒绝出单。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChannelAuthorizationAdapter implements ChannelAuthorizationPort {

    private final ChannelApi channelApi;

    @Override
    public ChannelAuthorization checkAuthorization(String channelId, String productId, String tenantId) {
        try {
            ApiResponse<ChannelAuthorizationResponse> response = channelApi.checkAuthorization(channelId, productId);
            if (response == null || !response.isSuccess() || response.getData() == null) {
                // 无有效答复不得并入「渠道不存在/未授权」——那会把依赖故障伪装成业务结论
                log.error("渠道授权校验失败, channelId={}, error={}", channelId,
                        response != null ? response.getMessage() : null);
                throw new RemoteDependencyUnavailableException(SystemErrorCode.DEPENDENCY_SERVICE_ERROR,
                        "渠道授权校验调用失败: channelId=" + channelId + ", errorCode="
                                + (response != null ? response.getCode() : null), null);
            }
            ChannelAuthorizationResponse data = response.getData();
            return new ChannelAuthorization(
                    channelId,
                    productId,
                    data.isChannelExists(),
                    CommonStatus.ACTIVE.name().equals(data.getChannelStatus()),
                    data.isProductAuthorized());
        } catch (FeignException exception) {
            log.warn("渠道授权校验调用失败: channelId={}, productId={}, tenantId={}, status={}",
                    channelId, productId, tenantId, exception.status(), exception);
            throw new RemoteDependencyUnavailableException(SystemErrorCode.DEPENDENCY_SERVICE_ERROR,
                    "渠道授权校验调用失败: channelId=" + channelId + ", status=" + exception.status(), exception);
        }
    }
}
