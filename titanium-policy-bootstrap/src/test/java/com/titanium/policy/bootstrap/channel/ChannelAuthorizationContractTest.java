package com.titanium.policy.bootstrap.channel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Component;

import com.titanium.policy.application.orchestration.issuance.orchestrator.IssuanceOrchestrator;
import com.titanium.policy.application.orchestration.issuance.validator.IssuanceChannelValidator;
import com.titanium.policy.infrastructure.adapter.channel.ChannelAuthorizationAdapter;
import com.titanium.policy.port.channel.ChannelAuthorizationPort;

/**
 * 渠道授权契约装配守卫（G13/AC-11）。
 * <p>
 * AC-11 要求读能力在「Port 接口 / Adapter 实现 / 生产调用方 / 守卫测试」四处同批出现。本类以前三处为
 * 断言对象，用<b>编译期绑定</b>的类引用（而非源码 grep）机械钉死——类被移动、改名、脱离实现关系即
 * 编译失败或断言失败；最后两条额外断言<b>接线完整性</b>：组件存在 ≠ 链路可达，校验器若未挂进
 * 出单编排器，渠道校验会在主链路上静默消失（组件扫描照样装配、启动照样成功）。
 * </p>
 */
class ChannelAuthorizationContractTest {

    @Test
    void portMustBeInterfaceLocatedInChannelSubpackage() {
        assertTrue(ChannelAuthorizationPort.class.isInterface(), "Port 必须是接口（六边形架构 Port）");
        assertEquals("com.titanium.policy.port.channel", ChannelAuthorizationPort.class.getPackageName(),
                "Port 须与 aggregate 平级置于 port/channel（按对端域拆子包）");
    }

    @Test
    void adapterMustImplementPortAndResideInInfrastructure() {
        assertTrue(ChannelAuthorizationPort.class.isAssignableFrom(ChannelAuthorizationAdapter.class),
                "Adapter 必须实现 Port");
        assertEquals("com.titanium.policy.infrastructure.adapter.channel", ChannelAuthorizationAdapter.class.getPackageName(),
                "Adapter 实现必须位于 infrastructure/adapter（禁止放 application）");
        assertNotNull(ChannelAuthorizationAdapter.class.getAnnotation(Component.class),
                "Adapter 须可被组件扫描装配");
    }

    @Test
    void validatorMustDependOnPortViaConstructor() {
        assertTrue(Arrays.stream(IssuanceChannelValidator.class.getDeclaredConstructors())
                        .anyMatch(constructor ->
                                Arrays.asList(constructor.getParameterTypes()).contains(ChannelAuthorizationPort.class)),
                "生产调用方（出单校验器）必须经构造器注入 Port");
    }

    @Test
    void issuanceOrchestratorMustWireValidator() {
        assertTrue(Arrays.stream(IssuanceOrchestrator.class.getDeclaredConstructors())
                        .anyMatch(constructor ->
                                Arrays.asList(constructor.getParameterTypes()).contains(IssuanceChannelValidator.class)),
                "出单编排器必须接线渠道校验器——组件存在不等于校验生效");
    }
}
