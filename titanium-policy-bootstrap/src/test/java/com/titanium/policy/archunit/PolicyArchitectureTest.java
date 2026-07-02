package com.titanium.policy.archunit;

import org.junit.jupiter.api.Test;

import com.titanium.buildtools.archunit.AbstractArchitectureGuardTest;

/**
 * 保单域架构守护测试：继承共享基类，仅提供本域根包。
 * 全部 DDD 分层/命名/依赖注入规则由 {@link AbstractArchitectureGuardTest} 提供，
 * 规则一处维护、各域复用，杜绝测试代码复制粘贴漂移。
 */
class PolicyArchitectureTest extends AbstractArchitectureGuardTest {

    @Override
    protected String basePackage() {
        return "com.titanium.policy";
    }

    /**
     * 启用「Web 层不得直接依赖领域命令/聚合根」严格隔离规则。
     * <p>
     * 基类默认 {@code @Disabled} 该规则（多数域 Controller 仍直接吃 domain command/aggregate）。 保单域已完成 Web
     * 层 Request/VO + Mapper 隔离改造（三个 Controller 只依赖 Web 层 Request/VO 与 应用层 command/query
     * 服务），故在本子类覆盖启用（不加 {@code @Disabled}），作为全域严格隔离的样板。
     * </p>
     */
    @Test
    @Override
    protected void webShouldNotDependOnDomainCommandsOrAggregates() {
        super.webShouldNotDependOnDomainCommandsOrAggregates();
    }
}
