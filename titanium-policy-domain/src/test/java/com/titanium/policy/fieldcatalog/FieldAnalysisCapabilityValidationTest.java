package com.titanium.policy.fieldcatalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.titanium.metadata.enums.policy.fieldcatalog.PolicyFieldMaskingPolicy;
import com.titanium.metadata.enums.policy.fieldcatalog.PolicyFieldObjectType;
import com.titanium.metadata.enums.policy.fieldcatalog.PolicyFieldSensitivityLevel;
import com.titanium.metadata.enums.policy.fieldcatalog.PolicyFieldValueType;
import com.titanium.metadata.errorcode.BaseErrorCode;
import com.titanium.metadata.errorcode.PolicyErrorCode;
import com.titanium.metadata.fieldcatalog.FieldAnalysisCapability;
import com.titanium.metadata.valueobject.RuleDecision;

/**
 * 「可分析」能力位四条不变量（G07/g07-03/AC-03）的单元测试。
 *
 * <p>🔴 <b>每条规则的用例都只用「仅违反该规则、其它规则一律不触发」的组合</b>——
 * 若用它例顺带覆盖，四条规则里有一条失效时无从分辨，判据就退了。
 * 逐条的依据（值类型 / collection / analyzable 三个维度的取值都据此挑）：</p>
 * <ul>
 *   <li>规则 1 用例取 INTEGER 标量：值类型可聚合（不触发规则 2）、非集合（不触发规则 3）、
 *       {@code groupable=false}（不触发规则 4）。</li>
 *   <li>规则 2 用例取 TEXT 标量 + {@code analyzable=true}（不触发规则 1）、非集合（不触发规则 3）。</li>
 *   <li>规则 3 用例取 TEXT 集合 + {@code aggregatable=false}（不触发规则 2）、
 *       {@code groupable=false}（不触发规则 4）。</li>
 *   <li>规则 4 用例取 INTEGER 标量 + {@code aggregatable=true}（INTEGER 可聚合，不触发规则 2）、
 *       非集合（不触发规则 3）。</li>
 * </ul>
 *
 * <p>另有两条横向判据：<b>短路顺序</b>（多违规时报第一条而非任意一条）与
 * <b>两条调用路径一致</b>（静态裁决方法与紧凑构造器必须给出同一个错误码——
 * 它们是「先问能不能」与「能不能都由它说了算」的两种用法，规则漂移即此处破裂）。</p>
 */
class FieldAnalysisCapabilityValidationTest {

    // ---------------------------------------------------------------- 规则 1

    @Test
    void shouldRejectInconsistentBitsWhenAnalyzableIsOff() {
        // 只违反规则 1：INTEGER 可聚合、非集合、groupable=false，故规则 2/3/4 均不适用。
        RuleDecision decision = PolicyFieldDescriptor.validateAnalysis(
                "policy.test.amount",
                FieldAnalysisCapability.of(false, true, false, false),
                PolicyFieldValueType.INTEGER,
                false);

        assertEquals(false, decision.passed(), "总开关关掉却声称可聚合，必须拒绝");
        assertEquals(PolicyErrorCode.POLICY_FIELD_ANALYSIS_INCONSISTENT, decision.errorCode());
        assertTrue(decision.defaultMessage().contains("policy.test.amount"), "错误须定位到字段");

        // 其余两位各自单独为 true 时同样拒绝（三个合取项各验一次，避免只覆盖 aggregatable）。
        for (FieldAnalysisCapability inconsistent : new FieldAnalysisCapability[] {
                FieldAnalysisCapability.of(false, false, true, false),
                FieldAnalysisCapability.of(false, false, false, true)}) {
            assertEquals(false,
                    PolicyFieldDescriptor
                            .validateAnalysis("policy.test.amount", inconsistent, PolicyFieldValueType.INTEGER, false)
                            .passed(),
                    "analyzable=false 时任一其它位为 true 都应拒绝: " + inconsistent);
        }

        // 边界正例：全 false 是「不参与分析」的合法表达（与 FieldAnalysisCapability.none() 一致）。
        assertEquals(true,
                PolicyFieldDescriptor
                        .validateAnalysis("policy.test.amount",
                                FieldAnalysisCapability.none(), PolicyFieldValueType.INTEGER, false)
                        .passed(),
                "analyzable=false 且其余全 false 必须放行");
    }

    // ---------------------------------------------------------------- 规则 2

    @Test
    void shouldRejectAggregatableOnNonMeasurableTypes() {
        // 只违反规则 2：TEXT/OBJECT/ARRAY 标量，analyzable=true 故规则 1 不适用、非集合故规则 3 不适用。
        for (PolicyFieldValueType valueType : new PolicyFieldValueType[] {
                PolicyFieldValueType.TEXT, PolicyFieldValueType.OBJECT, PolicyFieldValueType.ARRAY}) {
            RuleDecision decision = PolicyFieldDescriptor.validateAnalysis(
                    "policy.test.text",
                    FieldAnalysisCapability.of(true, true, false, false),
                    valueType,
                    false);

            assertEquals(false, decision.passed(), "不可度量类型不得声明可聚合: " + valueType);
            assertEquals(PolicyErrorCode.POLICY_FIELD_ANALYSIS_TYPE_UNSUPPORTED, decision.errorCode());
            assertTrue(decision.defaultMessage().contains(valueType.getCode()),
                    "错误须带上值类型以便定位: " + valueType);
        }

        // 边界正例：其余六种类型均可聚合（COUNT/SUM 口径下有意义）。
        for (PolicyFieldValueType valueType : new PolicyFieldValueType[] {
                PolicyFieldValueType.INTEGER,
                PolicyFieldValueType.DECIMAL,
                PolicyFieldValueType.BOOLEAN,
                PolicyFieldValueType.DATE,
                PolicyFieldValueType.DATETIME,
                PolicyFieldValueType.ENUM}) {
            assertEquals(true,
                    PolicyFieldDescriptor
                            .validateAnalysis("policy.test.measurable",
                                    FieldAnalysisCapability.of(true, true, false, false), valueType, false)
                            .passed(),
                    "可度量类型应放行: " + valueType);
        }
    }

    // ---------------------------------------------------------------- 规则 3

    @Test
    void shouldRejectCollectionFieldThatIsNotGroupable() {
        // 只违反规则 3：TEXT 集合 + aggregatable=false（不触发规则 2）、groupable=false（不触发规则 4）。
        RuleDecision decision = PolicyFieldDescriptor.validateAnalysis(
                "policy.insured.name",
                FieldAnalysisCapability.of(true, false, false, false),
                PolicyFieldValueType.TEXT,
                true);

        assertEquals(false, decision.passed(), "集合字段必须可分组，否则聚合结果无法按业务主体归并");
        assertEquals(PolicyErrorCode.POLICY_FIELD_ANALYSIS_COLLECTION_NOT_GROUPABLE, decision.errorCode());
        assertTrue(decision.defaultMessage().contains("policy.insured.name"));

        // 边界正例：集合 + groupable=true（filterable 必须同时为 true 才不算违反规则 4）。
        assertEquals(true,
                PolicyFieldDescriptor
                        .validateAnalysis("policy.insured.name",
                                FieldAnalysisCapability.of(true, false, true, true),
                                PolicyFieldValueType.TEXT, true)
                        .passed(),
                "集合字段可分组即放行");
    }

    // ---------------------------------------------------------------- 规则 4

    @Test
    void shouldRejectGroupableFieldThatIsNotFilterable() {
        // 只违反规则 4：INTEGER 标量可聚合（不触发规则 2）、非集合（不触发规则 3）。
        RuleDecision decision = PolicyFieldDescriptor.validateAnalysis(
                "policy.test.amount",
                FieldAnalysisCapability.of(true, true, true, false),
                PolicyFieldValueType.INTEGER,
                false);

        assertEquals(false, decision.passed(), "能分组必能过滤");
        assertEquals(PolicyErrorCode.POLICY_FIELD_ANALYSIS_GROUPABLE_NOT_FILTERABLE, decision.errorCode());
        assertTrue(decision.defaultMessage().contains("policy.test.amount"));
    }

    // ---------------------------------------------------------------- 短路顺序

    @Test
    void shouldReportFirstViolationInRuleOrder() {
        // 同时违反规则 2 与规则 3：TEXT 集合 + aggregatable=true + groupable=false。
        // 必须报规则 2（前者），证明四条是**有序短路**而非任取一条。
        RuleDecision decision = PolicyFieldDescriptor.validateAnalysis(
                "policy.test.multi",
                FieldAnalysisCapability.of(true, true, false, false),
                PolicyFieldValueType.TEXT,
                true);

        assertEquals(PolicyErrorCode.POLICY_FIELD_ANALYSIS_TYPE_UNSUPPORTED, decision.errorCode(),
                "多违规时必须报规则序中靠前的那条");
    }

    // ---------------------------------------------------------------- 构造器与静态裁决一致

    @Test
    void shouldMakeConstructorAndStaticCheckAgreeOnErrorCode() {
        // 同一份违规输入走两条路径：静态裁决返回错误码，紧凑构造器抛出携带同一错误码的异常。
        // 二者不一致即说明规则在两处各写了一份（本类存在的意义就是钉死这一点）。
        FieldAnalysisCapability invalid = FieldAnalysisCapability.of(true, true, true, false);
        PolicyFieldValueType valueType = PolicyFieldValueType.INTEGER;

        BaseErrorCode expected = PolicyFieldDescriptor
                .validateAnalysis("policy.test.amount", invalid, valueType, false)
                .errorCode();

        PolicyFieldCatalogValidationException thrown = assertThrows(
                PolicyFieldCatalogValidationException.class,
                () -> PolicyFieldDescriptor.scalar(
                        "policy.test.amount",
                        PolicyFieldObjectType.POLICY,
                        valueType,
                        "policy.field.test.amount",
                        PolicyFieldCapability.readOnly(),
                        PolicyFieldSensitivityLevel.INTERNAL,
                        PolicyFieldMaskingPolicy.NONE,
                        invalid));

        assertEquals(expected, thrown.getErrorCode(), "构造器与静态裁决必须给出同一个错误码");
        assertEquals(expected, PolicyErrorCode.POLICY_FIELD_ANALYSIS_GROUPABLE_NOT_FILTERABLE,
                "本用例注入的是规则 4 违规，前置断言防用例自身漂移");
    }

    // ---------------------------------------------------------------- 判空

    @Test
    void shouldRejectNullAnalysis() {
        PolicyFieldCatalogValidationException thrown = assertThrows(
                PolicyFieldCatalogValidationException.class,
                () -> PolicyFieldDescriptor.scalar(
                        "policy.test.amount",
                        PolicyFieldObjectType.POLICY,
                        PolicyFieldValueType.INTEGER,
                        "policy.field.test.amount",
                        PolicyFieldCapability.readOnly(),
                        PolicyFieldSensitivityLevel.INTERNAL,
                        PolicyFieldMaskingPolicy.NONE,
                        null));

        assertTrue(thrown.getMessage().contains("分析位"), "判空消息须点明是分析位缺失");
    }
}
