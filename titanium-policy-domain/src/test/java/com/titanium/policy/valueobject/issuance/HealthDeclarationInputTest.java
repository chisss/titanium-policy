package com.titanium.policy.valueobject.issuance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.titanium.policy.common.constant.PolicyConstants.LineExtendDataKey;

/**
 * 健康告知输入值对象测试（G12/g12-01 AC-01）
 * <p>
 * 锁死「全或全无」口径与宽容/严格分界：三个必答项（吸烟/身高/体重）齐全且身高体重为正才构造，
 * 缺任一或非法即 {@code null}（未提供）——<b>绝不代填默认值</b>。病史两键宽容承接（缺失/非列表视为空）。
 * </p>
 */
class HealthDeclarationInputTest {

    // ---------------------------------------------------------------- 正向解析

    @Test
    void parsesCompleteDeclarationWithHistoryLists() {
        Map<String, Object> extendData = declarationBlock(Map.of(
                LineExtendDataKey.HEALTH_DECLARATION_MEDICAL_HISTORY, List.of("高血压", "糖尿病"),
                LineExtendDataKey.HEALTH_DECLARATION_FAMILY_HISTORY, List.of("冠心病家族史"),
                LineExtendDataKey.HEALTH_DECLARATION_SMOKING, true,
                LineExtendDataKey.HEALTH_DECLARATION_HEIGHT_CM, new BigDecimal("175"),
                LineExtendDataKey.HEALTH_DECLARATION_WEIGHT_KG, new BigDecimal("80")));

        HealthDeclarationInput declaration = HealthDeclarationInput.fromExtendData(extendData);

        assertEquals(List.of("高血压", "糖尿病"), declaration.medicalHistory());
        assertEquals(List.of("冠心病家族史"), declaration.familyHistory());
        assertTrue(declaration.smoking());
        assertEquals(new BigDecimal("175"), declaration.heightCm());
        assertEquals(new BigDecimal("80"), declaration.weightKg());
    }

    @Test
    void treatsExplicitFalseSmokingAsProvidedRatherThanMissing() {
        // 🔴 吸烟=false 是「已告知不吸烟」，与「未告知」是两回事：解析器必须返回已构造对象，
        // 把 false 读成缺失会让低风险告知被降级成「无告知」
        Map<String, Object> extendData = declarationBlock(Map.of(
                LineExtendDataKey.HEALTH_DECLARATION_SMOKING, false,
                LineExtendDataKey.HEALTH_DECLARATION_HEIGHT_CM, 175,
                LineExtendDataKey.HEALTH_DECLARATION_WEIGHT_KG, 80));

        HealthDeclarationInput declaration = HealthDeclarationInput.fromExtendData(extendData);

        assertFalse(declaration.smoking());
        assertEquals(new BigDecimal("175"), declaration.heightCm());
        assertEquals(new BigDecimal("80"), declaration.weightKg());
    }

    @Test
    void acceptsStringFormsOfBooleanAndDecimals() {
        // 跨域 JSON 经不同序列化器时数字可能以字符串到达（"175"），布尔同理——宽容承接但语义不变
        Map<String, Object> extendData = declarationBlock(Map.of(
                LineExtendDataKey.HEALTH_DECLARATION_SMOKING, "TRUE",
                LineExtendDataKey.HEALTH_DECLARATION_HEIGHT_CM, " 175.5 ",
                LineExtendDataKey.HEALTH_DECLARATION_WEIGHT_KG, "80.25"));

        HealthDeclarationInput declaration = HealthDeclarationInput.fromExtendData(extendData);

        assertTrue(declaration.smoking());
        assertEquals(new BigDecimal("175.5"), declaration.heightCm());
        assertEquals(new BigDecimal("80.25"), declaration.weightKg());
    }

    @Test
    void defaultsHistoryListsToEmptyWhenAbsentOrNotLists() {
        Map<String, Object> extendData = declarationBlock(Map.of(
                LineExtendDataKey.HEALTH_DECLARATION_MEDICAL_HISTORY, "高血压",
                LineExtendDataKey.HEALTH_DECLARATION_SMOKING, false,
                LineExtendDataKey.HEALTH_DECLARATION_HEIGHT_CM, 175,
                LineExtendDataKey.HEALTH_DECLARATION_WEIGHT_KG, 80));

        HealthDeclarationInput declaration = HealthDeclarationInput.fromExtendData(extendData);

        // 非列表值不参与完整性判据（病史非必答），按空承接；家族史键完全缺失同样为空
        assertEquals(List.of(), declaration.medicalHistory());
        assertEquals(List.of(), declaration.familyHistory());
    }

    @Test
    void skipsNullElementsAndStringifiesOthersInHistoryLists() {
        Map<String, Object> block = new HashMap<>();
        block.put(LineExtendDataKey.HEALTH_DECLARATION_MEDICAL_HISTORY, Arrays.asList("高血压", null, 35));
        block.put(LineExtendDataKey.HEALTH_DECLARATION_SMOKING, true);
        block.put(LineExtendDataKey.HEALTH_DECLARATION_HEIGHT_CM, 175);
        block.put(LineExtendDataKey.HEALTH_DECLARATION_WEIGHT_KG, 80);

        HealthDeclarationInput declaration = HealthDeclarationInput.fromExtendData(declarationBlock(block));

        // null 元素跳过（避免列表内 null 传播到核保域评分），非字符串元素取字面量
        assertEquals(List.of("高血压", "35"), declaration.medicalHistory());
    }

    // ---------------------------------------------------------------- 未提供 / 声明不完整

    @Test
    void returnsNullWhenExtendDataIsNullOrHasNoDeclarationKey() {
        assertNull(HealthDeclarationInput.fromExtendData(null));
        assertNull(HealthDeclarationInput.fromExtendData(Map.of("otherKey", "value")));
    }

    @Test
    void returnsNullWhenDeclarationBlockIsNotAnObject() {
        // 块值非对象（字符串/列表）按未提供处理，不得抛类型转换异常打断出单链路
        assertNull(HealthDeclarationInput.fromExtendData(
                Map.of(LineExtendDataKey.HEALTH_DECLARATION, "有既往病史")));
        assertNull(HealthDeclarationInput.fromExtendData(
                Map.of(LineExtendDataKey.HEALTH_DECLARATION, List.of("高血压"))));
    }

    @Test
    void returnsNullWhenAnyRequiredAnswerIsMissing() {
        assertNull(HealthDeclarationInput.fromExtendData(declarationBlock(Map.of(
                LineExtendDataKey.HEALTH_DECLARATION_HEIGHT_CM, 175,
                LineExtendDataKey.HEALTH_DECLARATION_WEIGHT_KG, 80))));
        assertNull(HealthDeclarationInput.fromExtendData(declarationBlock(Map.of(
                LineExtendDataKey.HEALTH_DECLARATION_SMOKING, true,
                LineExtendDataKey.HEALTH_DECLARATION_WEIGHT_KG, 80))));
        assertNull(HealthDeclarationInput.fromExtendData(declarationBlock(Map.of(
                LineExtendDataKey.HEALTH_DECLARATION_SMOKING, true,
                LineExtendDataKey.HEALTH_DECLARATION_HEIGHT_CM, 175))));
    }

    @Test
    void returnsNullWhenHeightOrWeightIsNotPositiveOrUnparsable() {
        assertNull(HealthDeclarationInput.fromExtendData(declarationBlock(Map.of(
                LineExtendDataKey.HEALTH_DECLARATION_SMOKING, true,
                LineExtendDataKey.HEALTH_DECLARATION_HEIGHT_CM, 0,
                LineExtendDataKey.HEALTH_DECLARATION_WEIGHT_KG, 80))));
        assertNull(HealthDeclarationInput.fromExtendData(declarationBlock(Map.of(
                LineExtendDataKey.HEALTH_DECLARATION_SMOKING, true,
                LineExtendDataKey.HEALTH_DECLARATION_HEIGHT_CM, 175,
                LineExtendDataKey.HEALTH_DECLARATION_WEIGHT_KG, -5))));
        assertNull(HealthDeclarationInput.fromExtendData(declarationBlock(Map.of(
                LineExtendDataKey.HEALTH_DECLARATION_SMOKING, true,
                LineExtendDataKey.HEALTH_DECLARATION_HEIGHT_CM, "约175",
                LineExtendDataKey.HEALTH_DECLARATION_WEIGHT_KG, 80))));
        assertNull(HealthDeclarationInput.fromExtendData(declarationBlock(Map.of(
                LineExtendDataKey.HEALTH_DECLARATION_SMOKING, "有吸烟史",
                LineExtendDataKey.HEALTH_DECLARATION_HEIGHT_CM, 175,
                LineExtendDataKey.HEALTH_DECLARATION_WEIGHT_KG, 80))));
    }

    // ---------------------------------------------------------------- 夹具

    /** 构造携带健康告知块的段级 extendData */
    private Map<String, Object> declarationBlock(Map<String, Object> block) {
        Map<String, Object> extendData = new HashMap<>();
        extendData.put(LineExtendDataKey.HEALTH_DECLARATION, new HashMap<>(block));
        return extendData;
    }
}
