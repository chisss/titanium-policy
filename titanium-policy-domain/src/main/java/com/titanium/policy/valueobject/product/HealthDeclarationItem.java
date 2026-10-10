package com.titanium.policy.valueobject.product;

import java.util.Objects;

import com.titanium.metadata.enums.underwriting.HealthDeclarationHandling;
import com.titanium.metadata.enums.underwriting.HealthDeclarationQuestion;

/**
 * 告知项配置（policy 侧防腐镜像，G12/g12-02 AC-02）
 * <p>
 * product 域 {@code InsureCondition.healthDeclarationItems} 的六边形防腐承接。本域只消费三要素：
 * 问题编码（核对答案）、是否必答（缺失拒绝）、命中处置（{@link HealthDeclarationHandling#REJECT} 执行）；
 * 展示文案（{@code text}）属 product 侧配置属性，本域不消费故不承载。
 * </p>
 *
 * @param question 问题编码（权威值域，非空）
 * @param required 是否必答（true 时答案缺失即拒绝出单）
 * @param handling 命中处置；{@code null} 表示产品域给出了本域未认识的处置码（滚动升级窗口的扩展项），
 *                 按「仅必答校验、不执行处置」处理
 */
public record HealthDeclarationItem(HealthDeclarationQuestion question, boolean required,
                                    HealthDeclarationHandling handling) {

    public HealthDeclarationItem {
        Objects.requireNonNull(question, "告知项问题编码不得为空");
    }

    /**
     * 答案是否未作答（缺失或空白字符串）。
     * <p>
     * 🔴 「未作答」与「答否」必须可区分：前者（必答项）补答即可重试，后者是有效答案不拦截。
     * </p>
     *
     * @param answer 客户对本题的答案载荷（JSON 反序列化后的对象）
     * @return 未作答返回 {@code true}
     */
    public boolean isUnanswered(Object answer) {
        return answer == null || (answer instanceof String text && text.isBlank());
    }

    /**
     * 答案是否触发本项拒绝处置。
     * <p>
     * 🔴 仅处置为 {@link HealthDeclarationHandling#REJECT} 时判定；其余处置（人工复核/仅记录）与
     * 未识别处置（null，滚动升级窗口的扩展码）一律不触发——当前只执行「必答校验 + REJECT 拦截」。
     * </p>
     *
     * @param answer 客户对本题的答案载荷
     * @return 命中拒绝返回 {@code true}
     */
    public boolean rejects(Object answer) {
        return handling == HealthDeclarationHandling.REJECT && isAffirmative(answer);
    }

    /**
     * 答案是否为肯定（JSON 布尔 {@code true} 或字符串 {@code "true"}，兼容两种前端报文字面）。
     */
    private static boolean isAffirmative(Object answer) {
        return Boolean.TRUE.equals(answer)
                || (answer instanceof String text && "true".equalsIgnoreCase(text.trim()));
    }
}
