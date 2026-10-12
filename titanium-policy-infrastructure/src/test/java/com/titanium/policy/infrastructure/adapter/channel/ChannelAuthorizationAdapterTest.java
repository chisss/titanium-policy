package com.titanium.policy.infrastructure.adapter.channel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.titanium.channel.api.ChannelApi;
import com.titanium.channel.api.response.channel.ChannelAuthorizationResponse;
import com.titanium.metadata.errorcode.SystemErrorCode;
import com.titanium.metadata.response.ApiResponse;
import com.titanium.policy.exception.RemoteDependencyUnavailableException;
import com.titanium.policy.valueobject.channel.ChannelAuthorization;

import feign.FeignException;
import feign.Request;

/**
 * 渠道授权校验适配器测试（G13/g13-08，m21-02 失败不折叠契约）。
 *
 * <p>两条判据主体：① 对端状态枚举名必须被翻译为布尔「可用于出单」（"ACTIVE" 之外一律 false，
 * 且渠道不存在时状态为 null 不得炸）；② 依赖失败一律抛 {@code RemoteDependencyUnavailableException}
 * ——「错误信封 / 无载荷 / Feign 异常」三种形态都不得折叠为「未授权」的业务结论。</p>
 */
class ChannelAuthorizationAdapterTest {

    private static final String CHANNEL_ID = "CHANNEL_001";
    private static final String PRODUCT_ID = "PRODUCT_001";
    private static final String TENANT_ID = "TENANT_001";

    /** 仅供 FeignException 携带的占位请求（同 RemoteFailureNoFoldingTest：Request 为 final 类不可 mock） */
    private static final Request DUMMY_REQUEST = Request.create(Request.HttpMethod.GET, "/probe",
            java.util.Map.of(), new byte[0], java.nio.charset.StandardCharsets.UTF_8);

    private final ChannelApi channelApi = mock(ChannelApi.class);

    private final ChannelAuthorizationAdapter adapter = new ChannelAuthorizationAdapter(channelApi);

    @Test
    void shouldTranslateActiveStatusToUsableChannel() {
        when(channelApi.checkAuthorization(CHANNEL_ID, PRODUCT_ID))
                .thenReturn(ApiResponse.success(response(true, "ACTIVE", true)));

        ChannelAuthorization authorization = adapter.checkAuthorization(CHANNEL_ID, PRODUCT_ID, TENANT_ID);

        assertEquals(CHANNEL_ID, authorization.channelId());
        assertEquals(PRODUCT_ID, authorization.productId());
        assertTrue(authorization.channelExists());
        assertTrue(authorization.channelActive(), "对端状态名 ACTIVE 必须翻译为可用");
        assertTrue(authorization.productAuthorized());
        assertTrue(authorization.authorized());
    }

    @Test
    void shouldTranslateNonActiveStatusToUnusableChannel() {
        when(channelApi.checkAuthorization(CHANNEL_ID, PRODUCT_ID))
                .thenReturn(ApiResponse.success(response(true, "INACTIVE", true)));

        ChannelAuthorization authorization = adapter.checkAuthorization(CHANNEL_ID, PRODUCT_ID, TENANT_ID);

        assertTrue(authorization.channelExists());
        assertFalse(authorization.channelActive(), "ACTIVE 之外的任何状态名都不得被当作可用");
        assertFalse(authorization.authorized());
    }

    @Test
    void shouldMapMissingChannelAsConclusionNotFailure() {
        when(channelApi.checkAuthorization(CHANNEL_ID, PRODUCT_ID))
                .thenReturn(ApiResponse.success(response(false, null, false)));

        ChannelAuthorization authorization = adapter.checkAuthorization(CHANNEL_ID, PRODUCT_ID, TENANT_ID);

        // 渠道不存在是合法结论（状态为 null），不是依赖失败——不得抛异常
        assertFalse(authorization.channelExists());
        assertFalse(authorization.channelActive());
        assertFalse(authorization.authorized());
    }

    @Test
    @DisplayName("错误信封：抛出具名异常，而非折叠成「未授权」的业务结论")
    void shouldThrowWhenDownstreamAnswersWithFailureCode() {
        when(channelApi.checkAuthorization(anyString(), anyString()))
                .thenReturn(ApiResponse.error(SystemErrorCode.RESOURCE_NOT_FOUND, "渠道不存在"));

        assertThrows(RemoteDependencyUnavailableException.class,
                () -> adapter.checkAuthorization(CHANNEL_ID, PRODUCT_ID, TENANT_ID));
    }

    @Test
    @DisplayName("成功信封但无载荷：同样抛出具名异常")
    void shouldThrowWhenPayloadMissing() {
        when(channelApi.checkAuthorization(anyString(), anyString()))
                .thenReturn(ApiResponse.success(null));

        assertThrows(RemoteDependencyUnavailableException.class,
                () -> adapter.checkAuthorization(CHANNEL_ID, PRODUCT_ID, TENANT_ID));
    }

    @Test
    @DisplayName("Feign 异常（连接被拒/超时/5xx）：抛出具名异常，不折叠")
    void shouldThrowOnFeignFailure() {
        when(channelApi.checkAuthorization(anyString(), anyString()))
                .thenThrow(new FeignException.ServiceUnavailable("down", DUMMY_REQUEST, null, null));

        assertThrows(RemoteDependencyUnavailableException.class,
                () -> adapter.checkAuthorization(CHANNEL_ID, PRODUCT_ID, TENANT_ID));
    }

    /** 契约响应桩：调用方显式给出三要素（@Data 无 builder，逐 set） */
    private static ChannelAuthorizationResponse response(boolean exists, String status, boolean authorized) {
        ChannelAuthorizationResponse response = new ChannelAuthorizationResponse();
        response.setChannelId(CHANNEL_ID);
        response.setProductId(PRODUCT_ID);
        response.setChannelExists(exists);
        response.setChannelStatus(status);
        response.setProductAuthorized(authorized);
        response.setAuthorized(exists && "ACTIVE".equals(status) && authorized);
        return response;
    }
}
