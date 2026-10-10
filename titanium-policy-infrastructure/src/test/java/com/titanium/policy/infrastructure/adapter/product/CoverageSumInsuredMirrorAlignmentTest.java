package com.titanium.policy.infrastructure.adapter.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.titanium.policy.valueobject.product.CoverageSumInsuredConfig;
import com.titanium.product.api.response.config.CoverageSumInsuredConfigResponse;
import com.titanium.product.api.response.config.CoverageSumInsuredTierResponse;

/**
 * 保额档次防腐镜像与对端契约的分量对齐守卫（G02/AC-09）
 * <p>
 * 保单域的 {@link CoverageSumInsuredConfig} 是 product 域契约的防腐镜像，<b>只承载数据</b>：对端
 * 新增字段时镜像不同批补齐，就会在翻译环节<b>静默丢字段</b>——编译通过、既有用例全绿，
 * 只有真机上「某个新配置不生效」这种无从归因的现象。
 * </p>
 * <p>
 * 故以「读对端 api 契约的 record 分量逐一对齐」把它变成构建期硬约束。判据基于<b>编译产物</b>
 * （{@code getRecordComponents()}）而非源码文本，对端改契约即反射可见。
 * </p>
 */
@DisplayName("保额档次镜像与对端契约的分量对齐")
class CoverageSumInsuredMirrorAlignmentTest {

    /** 绝对下界：防止两侧同时取到空分量集合时「集合相等」恒真而假绿。 */
    private static final int MIN_CONTRACT_COMPONENTS = 4;

    @Test
    @DisplayName("责任配置镜像：分量名称与类型与对端契约逐一对应")
    void coverageMirrorShouldCoverEveryContractComponent() {
        assertMirrors(CoverageSumInsuredConfigResponse.class, CoverageSumInsuredConfig.class);
    }

    @Test
    @DisplayName("档位镜像：分量名称与类型与对端契约逐一对应")
    void tierMirrorShouldCoverEveryContractComponent() {
        assertMirrors(CoverageSumInsuredTierResponse.class, CoverageSumInsuredConfig.SumInsuredTier.class);
    }

    /**
     * 逐分量比对：名称集合必须完全相等，同名分量类型必须一致。
     * <p>
     * 唯一允许的例外是 {@code allowCustomAmount}：对端为包装类型 {@code Boolean}，镜像为原始
     * {@code boolean}——翻译时以 {@code Boolean.TRUE.equals(...)} 承接，缺失即「不开放自由填」
     * （失败关闭，不静默放行）。
     * </p>
     */
    private void assertMirrors(Class<?> contract, Class<?> mirror) {
        Map<String, Class<?>> contractComponents = components(contract);
        Map<String, Class<?>> mirrorComponents = components(mirror);

        assertTrue(contractComponents.size() >= MIN_CONTRACT_COMPONENTS,
                () -> "对端契约 " + contract.getSimpleName() + " 的分量数异常偏少（" + contractComponents.size()
                        + "），判据可能已失效");

        assertEquals(contractComponents.keySet(), mirrorComponents.keySet(),
                () -> "镜像 " + mirror.getSimpleName() + " 与对端契约 " + contract.getSimpleName()
                        + " 的分量集合不一致：对端加/删字段必须同批补齐镜像");

        contractComponents.forEach((name, contractType) -> {
            Class<?> mirrorType = mirrorComponents.get(name);
            if (contractType == Boolean.class && mirrorType == boolean.class) {
                return;
            }
            assertEquals(contractType, mirrorType,
                    () -> "分量 " + name + " 类型不一致：对端 " + contractType + "，镜像 " + mirrorType);
        });
    }

    private Map<String, Class<?>> components(Class<?> type) {
        return Arrays.stream(type.getRecordComponents())
                .collect(Collectors.toMap(component -> component.getName(), component -> component.getType(),
                        (left, right) -> left, LinkedHashMap::new));
    }
}
