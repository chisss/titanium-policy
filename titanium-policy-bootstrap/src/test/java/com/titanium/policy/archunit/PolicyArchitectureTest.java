package com.titanium.policy.archunit;

import com.titanium.buildtools.archunit.AbstractArchitectureGuardTest;

/**
 * 本域架构守护测试：继承共享基类，仅提供本域根包（或本域差异项）。
 * <p>
 * GOV-02（m27-05）起基类规则<b>默认全部开启</b>：存量违规由 {@code FreezingArchRule} 冻结在
 * {@code archunit_store/}，只有新增违规判红；确属语义不适用或整改债者，必须以
 * {@code @Disabled("owner=…; until=YYYY-MM-DD; reason=…")} 显式豁免，并由基类元测试校验格式与到期日。
 * </p>
 */
class PolicyArchitectureTest extends AbstractArchitectureGuardTest {

    @Override
    protected String basePackage() {
        return "com.titanium.policy";
    }
}
