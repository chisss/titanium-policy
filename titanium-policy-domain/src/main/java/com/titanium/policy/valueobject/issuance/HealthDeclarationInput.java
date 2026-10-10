package com.titanium.policy.valueobject.issuance;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.titanium.policy.common.constant.PolicyConstants.LineExtendDataKey;

/**
 * 健康告知输入值对象（G12/g12-01 AC-01）
 * <p>
 * 出单请求段级 {@code extendData} 中「健康告知」块（键契约见
 * {@link LineExtendDataKey#HEALTH_DECLARATION}）在本域的解析载体：由出单 Saga 从
 * {@code InsuranceLine.extendData} 提取后，随 {@code UnderwritingDecisionRequest}
 * 经核保网关透传核保域（其权威值对象为 underwriting 域 {@code HealthDeclaration}，
 * 本类字段与之逐项对齐）。
 * </p>
 * <p>
 * 🔴 <b>「全或全无」口径</b>：核保域 {@code HealthDeclaration} 对身高/体重强校验（必须为正）、
 * 吸烟项为无三态原始 {@code boolean}，故本类只在<b>三个必答项（吸烟/身高/体重）齐全且身高体重为正</b>
 * 时构造；缺任一即 {@link #fromExtendData} 返回 {@code null}（Saga 留痕告警、按未提供处理），
 * <b>绝不以默认值代填</b>——「没告知」与「告知为低风险」必须在核保入参层可区分。
 * </p>
 * <p>
 * 🔴 <b>本类不做构造器强校验</b>：不变量与「全或全无」的保证由唯一工厂 {@link #fromExtendData} 承担；
 * 跨域链路的最终防线在核保域 {@code HealthDeclaration} 紧凑构造器（非法即拒收），
 * 本域不重复抛错（也避免在值对象构造器中新增 IAE 触发 IAE/ISE 棘轮守卫）。
 * </p>
 *
 * @param medicalHistory 既往病史（无则为空列表）
 * @param familyHistory  家族遗传病史（无则为空列表）
 * @param smoking        是否吸烟（必答）
 * @param heightCm       身高（厘米，必答且为正）
 * @param weightKg       体重（千克，必答且为正）
 */
public record HealthDeclarationInput(List<String> medicalHistory, List<String> familyHistory, boolean smoking,
                                     BigDecimal heightCm, BigDecimal weightKg) {

    /**
     * 从段级 {@code extendData} 解析健康告知（唯一工厂）。
     * <p>
     * 解析规则：键缺失或值非对象块 → {@code null}（未提供）；三个必答项
     * （{@link LineExtendDataKey#HEALTH_DECLARATION_SMOKING 吸烟}/
     * {@link LineExtendDataKey#HEALTH_DECLARATION_HEIGHT_CM 身高}/
     * {@link LineExtendDataKey#HEALTH_DECLARATION_WEIGHT_KG 体重}）缺任一或身高体重非正 → {@code null}
     * （声明不完整，与「未提供」同归未提供，由调用方按 {@code containsKey} 区分留痕）。
     * 病史两键宽容承接（缺失或非列表视为空）。
     * </p>
     * <p>
     * 🔴 调用方需先以 {@code containsKey(HEALTH_DECLARATION)} 区分「缺失」与「声明不完整」两种告警；
     * 本方法对二者同返回 {@code null}。
     * </p>
     *
     * @param extendData 段级扩展数据（可为 null）
     * @return 完整健康告知；未提供或声明不完整时返回 null
     */
    public static HealthDeclarationInput fromExtendData(Map<String, Object> extendData) {
        if (extendData == null) {
            return null;
        }
        Object rawBlock = extendData.get(LineExtendDataKey.HEALTH_DECLARATION);
        if (!(rawBlock instanceof Map<?, ?> block)) {
            return null;
        }
        Boolean smoking = toBoolean(block.get(LineExtendDataKey.HEALTH_DECLARATION_SMOKING));
        BigDecimal heightCm = toPositiveDecimal(block.get(LineExtendDataKey.HEALTH_DECLARATION_HEIGHT_CM));
        BigDecimal weightKg = toPositiveDecimal(block.get(LineExtendDataKey.HEALTH_DECLARATION_WEIGHT_KG));
        if (smoking == null || heightCm == null || weightKg == null) {
            return null;
        }
        return new HealthDeclarationInput(toStringList(block.get(LineExtendDataKey.HEALTH_DECLARATION_MEDICAL_HISTORY)),
                toStringList(block.get(LineExtendDataKey.HEALTH_DECLARATION_FAMILY_HISTORY)),
                smoking, heightCm, weightKg);
    }

    /** 布尔解析：仅接受布尔值（字符串 "true"/"false" 宽容承接，其余视为未提供）。 */
    private static Boolean toBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String text) {
            String trimmed = text.trim();
            if ("true".equalsIgnoreCase(trimmed)) {
                return Boolean.TRUE;
            }
            if ("false".equalsIgnoreCase(trimmed)) {
                return Boolean.FALSE;
            }
        }
        return null;
    }

    /** 正数解析：数字或可解析字符串转 BigDecimal，非正或不可解析返回 null。 */
    private static BigDecimal toPositiveDecimal(Object value) {
        BigDecimal decimal = null;
        if (value instanceof Number number) {
            decimal = new BigDecimal(number.toString());
        } else if (value instanceof String text && !text.isBlank()) {
            try {
                decimal = new BigDecimal(text.trim());
            } catch (NumberFormatException ignored) {
                // 不可解析字符串视为未提供（完整性判据的一部分，非静默代填）
                return null;
            }
        }
        return decimal != null && decimal.signum() > 0 ? decimal : null;
    }

    /** 字符串列表解析：缺失或非列表返回空列表，元素 null 跳过（病史两键的宽容承接）。 */
    private static List<String> toStringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (Object item : list) {
            if (item != null) {
                result.add(String.valueOf(item));
            }
        }
        return List.copyOf(result);
    }
}
