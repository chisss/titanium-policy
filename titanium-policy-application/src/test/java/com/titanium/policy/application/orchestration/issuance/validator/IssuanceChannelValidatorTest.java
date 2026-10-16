package com.titanium.policy.application.orchestration.issuance.validator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import com.titanium.metadata.errorcode.PolicyErrorCode;
import com.titanium.metadata.errorcode.SystemErrorCode;
import com.titanium.metadata.valueobject.RuleDecision;
import com.titanium.policy.exception.RemoteDependencyUnavailableException;
import com.titanium.policy.port.channel.ChannelAuthorizationPort;
import com.titanium.policy.valueobject.channel.ChannelAuthorization;

/**
 * 出单渠道授权校验器测试（G13/g13-08）。
 *
 * <p>覆盖「通过 / 三要素各拒 / 渠道域不可达（fail-closed）/ 渠道非必填跳过」六种形态。两条
 * {@code verify(..., never())} 短路断言是判据主体：只看裁决结果分辨不出「跳过校验」与
 * 「查了但通过」，也分辨不出「失败关闭」与「异常上抛」。</p>
 */
class IssuanceChannelValidatorTest {

    private static final String TENANT_ID = "TENANT_001";
    private static final String CHANNEL_ID = "CHANNEL_001";
    private static final String PRODUCT_ID = "PRODUCT_001";

    private final ChannelAuthorizationPort channelAuthorizationPort = mock(ChannelAuthorizationPort.class);

    private final IssuanceChannelValidator validator = new IssuanceChannelValidator(channelAuthorizationPort);

    @Test
    void shouldPassWhenChannelAuthorized() {
        when(channelAuthorizationPort.checkAuthorization(CHANNEL_ID, PRODUCT_ID, TENANT_ID))
                .thenReturn(new ChannelAuthorization(CHANNEL_ID, PRODUCT_ID, true, true, true));

        RuleDecision decision = validator.validate(CHANNEL_ID, PRODUCT_ID, TENANT_ID);

        assertTrue(decision.passed());
    }

    @Test
    void shouldRejectWithNotExistWhenChannelMissing() {
        when(channelAuthorizationPort.checkAuthorization(CHANNEL_ID, PRODUCT_ID, TENANT_ID))
                .thenReturn(new ChannelAuthorization(CHANNEL_ID, PRODUCT_ID, false, false, false));

        RuleDecision decision = validator.validate(CHANNEL_ID, PRODUCT_ID, TENANT_ID);

        assertFalse(decision.passed());
        assertEquals(PolicyErrorCode.ISSUANCE_CHANNEL_NOT_EXIST, decision.errorCode());
    }

    @Test
    void shouldRejectWithNotActiveWhenChannelInactive() {
        when(channelAuthorizationPort.checkAuthorization(CHANNEL_ID, PRODUCT_ID, TENANT_ID))
                .thenReturn(new ChannelAuthorization(CHANNEL_ID, PRODUCT_ID, true, false, true));

        RuleDecision decision = validator.validate(CHANNEL_ID, PRODUCT_ID, TENANT_ID);

        assertFalse(decision.passed());
        assertEquals(PolicyErrorCode.ISSUANCE_CHANNEL_NOT_ACTIVE, decision.errorCode(),
                "产品关联有效但渠道停用，错误码不得指向「未授权」");
    }

    @Test
    void shouldRejectWithProductNotAuthorizedWhenRelationMissing() {
        when(channelAuthorizationPort.checkAuthorization(CHANNEL_ID, PRODUCT_ID, TENANT_ID))
                .thenReturn(new ChannelAuthorization(CHANNEL_ID, PRODUCT_ID, true, true, false));

        RuleDecision decision = validator.validate(CHANNEL_ID, PRODUCT_ID, TENANT_ID);

        assertFalse(decision.passed());
        assertEquals(PolicyErrorCode.ISSUANCE_CHANNEL_PRODUCT_NOT_AUTHORIZED, decision.errorCode());
    }

    @Test
    void shouldFailClosedWhenChannelServiceUnavailable() {
        when(channelAuthorizationPort.checkAuthorization(CHANNEL_ID, PRODUCT_ID, TENANT_ID))
                .thenThrow(new RemoteDependencyUnavailableException(SystemErrorCode.DEPENDENCY_SERVICE_ERROR,
                        "channel service down"));

        RuleDecision decision = validator.validate(CHANNEL_ID, PRODUCT_ID, TENANT_ID);

        // 🔴 R-3 fail-closed：依赖故障必须拒绝出单，且错误码须指向「暂不可用」而非业务拒绝
        assertFalse(decision.passed());
        assertEquals(PolicyErrorCode.ISSUANCE_CHANNEL_AUTHORIZATION_UNAVAILABLE, decision.errorCode());
    }

    @Test
    void shouldSkipWhenChannelIdAbsent() {
        assertTrue(validator.validate(null, PRODUCT_ID, TENANT_ID).passed(), "无渠道历史出单不阻断");
        assertTrue(validator.validate("  ", PRODUCT_ID, TENANT_ID).passed(), "空白渠道ID 视同缺省");

        // any() 可匹配 null 参数——用 anyString() 会漏掉「null 时仍发起调用」的违规形态
        verify(channelAuthorizationPort, never()).checkAuthorization(any(), any(), any());
    }
}
