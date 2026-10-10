package com.titanium.policy.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.titanium.policy.common.enums.RiskAssessmentStep;
import com.titanium.policy.common.enums.RuleEngineDecision;
import com.titanium.policy.service.impl.RiskAssessmentDomainServiceImpl;
import com.titanium.policy.valueobject.RiskAssessmentDecision;

/**
 * 风控裁决领域服务单元测试
 * <p>
 * 领域服务无 Port/无容器依赖，直接 {@code new} 出来用纯 JUnit 测试，验证风控裁决纯规则。
 * </p>
 */
class RiskAssessmentDomainServiceTest {

    private RiskAssessmentDomainService domainService;

    @BeforeEach
    void setUp() {
        domainService = new RiskAssessmentDomainServiceImpl();
    }

    @Test
    @DisplayName("仅基础自动核保依赖规则引擎，其余步骤为本地规则")
    void shouldIdentifyRuleEngineDependentSteps() {
        assertTrue(domainService.requiresRuleEngine(RiskAssessmentStep.BASIC_UNDERWRITING));
        assertFalse(domainService.requiresRuleEngine(RiskAssessmentStep.BLACKLIST_CHECK));
        assertFalse(domainService.requiresRuleEngine(RiskAssessmentStep.DUPLICATE_CHECK));
        assertFalse(domainService.requiresRuleEngine(RiskAssessmentStep.MANUAL_UNDERWRITING));
        assertFalse(domainService.requiresRuleEngine(RiskAssessmentStep.AML_CHECK));
        assertFalse(domainService.requiresRuleEngine(RiskAssessmentStep.HEALTH_DECLARATION_CHECK));
    }

    @Test
    @DisplayName("依赖规则引擎的步骤：裁决通过与否随规则引擎结果")
    void shouldJudgeByRuleEngineOutcome() {
        RiskAssessmentDecision passed = domainService.judge(RiskAssessmentStep.BASIC_UNDERWRITING,
                RuleEngineDecision.PASS);
        assertTrue(passed.passed());
        assertEquals(RiskAssessmentStep.BASIC_UNDERWRITING, passed.step());

        RiskAssessmentDecision rejected = domainService.judge(RiskAssessmentStep.BASIC_UNDERWRITING,
                RuleEngineDecision.REJECT);
        assertFalse(rejected.passed());
        assertTrue(rejected.reason().contains("规则引擎"));

        RiskAssessmentDecision referred = domainService.judge(RiskAssessmentStep.BASIC_UNDERWRITING,
                RuleEngineDecision.REFER);
        assertTrue(referred.passed());
        assertTrue(referred.reason().contains("后续核保"));
    }

    @Test
    @DisplayName("本地风控步骤：人工核保放行以推进流程，黑名单/重复投保/反洗钱默认通过")
    void shouldPassLocalSteps() {
        assertTrue(domainService.judgeLocal(RiskAssessmentStep.MANUAL_UNDERWRITING).passed());
        assertTrue(domainService.judgeLocal(RiskAssessmentStep.BLACKLIST_CHECK).passed());
        assertTrue(domainService.judgeLocal(RiskAssessmentStep.DUPLICATE_CHECK).passed());
        assertTrue(domainService.judgeLocal(RiskAssessmentStep.AML_CHECK).passed());
    }

    @Test
    @DisplayName("健康告知步骤：显式『委托核保』放行，不得并入默认通过（G12/g12-01）")
    void healthDeclarationCheckIsExplicitlyDelegatedToUnderwriting() {
        // 行为层：放行——判定权在核保域（告知要素随核保请求透传），本步骤不阻断同步出单流程
        assertTrue(domainService.judgeLocal(RiskAssessmentStep.HEALTH_DECLARATION_CHECK).passed());

        // 结构层：「委托放行」与「默认通过」行为同形（都返回 pass），仅行为断言无法区分二者，故以源码扫描
        // 固化结构——judgeLocal 必须为 HEALTH_DECLARATION_CHECK 保留显式分支，并入默认通过（或注释掉分支）
        // 即失败。手法同 CrossTenantCommandGuardTest 的结构断言。
        String code = readServiceImplSource();
        assertTrue(code.contains("if (step == RiskAssessmentStep.HEALTH_DECLARATION_CHECK)"),
                "judgeLocal 中健康告知须保留显式『委托核保』分支，不得并入默认通过");
    }

    // ==================== 结构断言辅助（源码扫描） ====================

    /** 领域服务实现源码（已剥离行注释与块注释，避免注释中的字面量造成假绿） */
    private static String readServiceImplSource() {
        Path file = resolveServiceImplSource();
        try {
            return Files.readString(file, StandardCharsets.UTF_8)
                    .replaceAll("//[^\\n]*", "")
                    .replaceAll("(?s)/\\*.*?\\*/", "");
        } catch (IOException e) {
            throw new IllegalStateException("读取领域服务源码失败: " + file, e);
        }
    }

    /** 自模块目录向上定位领域服务实现源码（surefire 与 IDE 的工作目录可能不同） */
    private static Path resolveServiceImplSource() {
        String relative = "src/main/java/com/titanium/policy/service/impl/RiskAssessmentDomainServiceImpl.java";
        Path current = Paths.get("").toAbsolutePath();
        for (int depth = 0; depth < 6 && current != null; depth++) {
            Path candidate = current.resolve(relative);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("未能定位领域服务源码，当前目录: " + Paths.get("").toAbsolutePath());
    }
}
