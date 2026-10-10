package com.titanium.policy.common.constant;

import com.titanium.metadata.topic.CrossDomainTopics;

/**
 * 保单系统常量类
 */
public class PolicyConstants {
    public static final String POLICY_SYSTEM = "POLICY_SYSTEM";

    /** 统一出单自动提交意向单的变更原因（落库字符串，红线 17：必须收敛到常量而非散落字面量） */
    public static final String ISSUANCE_AUTO_SUBMIT_REASON = "统一出单自动提交意向单";

    // 保单状态已统一为 com.titanium.metadata.enums.policy.PolicyEnum.PolicyStatus
    // 与本地 com.titanium.policy.common.enums.PolicyStatusCode，此处原 PolicyStatus 字符串常量已删除。
    // 事件类型已迁移为枚举 com.titanium.policy.common.enums.PolicyEventType，此处原 EventType 字符串常量已删除。

    /**
     * 消息队列主题常量
     */
    public static class KafkaTopic {
        public static final String POLICY_CREATED = CrossDomainTopics.POLICY_CREATED; // 保单创建事件主题
        public static final String POLICY_ACTIVATED = CrossDomainTopics.POLICY_ACTIVATED; // 保单激活事件主题
        public static final String POLICY_ISSUED = CrossDomainTopics.POLICY_ISSUED; // 保单签发事件主题（供监管采集/自动分保消费）
        // 原 POLICY_EVENTS（titanium.policy.events，主题命名空间常量而非具体主题）与
        // POLICY_EXPIRED / POLICY_CANCELLED 自声明起从无发布点，已删除（m5-903）。
        // 本域实际外发仅上列三个主题（见 KafkaEventPublisher）；保单满期/注销事件不出域，
        // 若未来确需外发，须与消费端**成对建设**后重新登记进跨域事件目录，勿单侧新增孤儿主题。
    }

    /**
     * 车险 NCD（无赔款优待）取证字段的标的属性键（G11/g11-03 AC-04）
     * <p>
     * NCD 反算结果（产品域 {@code NcdResolutionResult}）随出单落入标的属性包，本类是该属性包的
     * **键契约**：出单侧按此写入，读侧投影 {@code PolicyLineProjectionEventHandler.saveSubjects}
     * 按此解析并落 {@code t_policy_subject} 的 {@code ncd_*} 六列。两侧共用一个常量源，
     * 键名漂移即编译期可见（不再靠字符串巧合对齐）。
     * </p>
     * <p>
     * 🔴 <b>{@link #NCD} 是存量录入键，沿用不改名</b>——产品目录 AUTO 险种的必填标的属性
     * （{@code licensePlate/vin/firstRegistrationDate/usageType/ncd}）即用此键，改名会让既有产品
     * 配置的必填校验落空。它原本是**人工录入的系数**，g11-03 起其权威来源改为反算结果，
     * 键名不变以免动产品配置面。
     * </p>
     * <p>
     * 🔴 <b>R-3 未裁期间的契约状态</b>：档位与系数值未确认，产品侧规则表为空，故出单链路**尚未**
     * 自动调用反算（一旦调用即 fail-closed 拒绝出单）。本组键此刻只定义契约：属性包未携带取证键时，
     * 对应列一律为 {@code NULL}——<b>绝不以默认系数 1.0 或默认区间填充</b>，
     * 「查不到」与「无赔款」在落库层同样必须可区分。
     * </p>
     */
    public static class SubjectAttributeKey {

        /** NCD 系数（车险标的属性的存量键名，权威来源自 g11-03 起为反算结果） */
        public static final String NCD = "ncd";

        /** 反算所用统计区间内的历史赔案条数（取证：条数） */
        public static final String NCD_CLAIM_COUNT = "ncdClaimCount";

        /** 统计区间起点（含，ISO-8601 日期时间；取证：区间） */
        public static final String NCD_PERIOD_FROM = "ncdPeriodFrom";

        /** 统计区间止点（不含，ISO-8601 日期时间；取证：区间） */
        public static final String NCD_PERIOD_TO = "ncdPeriodTo";

        /** 反算所用规则集编码（取证：规则版本） */
        public static final String NCD_RULE_CODE = "ncdRuleCode";

        /** 反算所用规则集版本（取证：规则版本） */
        public static final String NCD_RULE_VERSION = "ncdRuleVersion";
    }

    /**
     * 险种段特有要素（{@code extendData}）的键契约（G12/g12-01 AC-01）
     * <p>
     * {@code IssuancePlanLine.extendData} 是出单请求中<b>段级特化要素</b>的载体：前端经
     * {@code SubmitIssuanceRequest.PlanLine.extendData} 提交，随 {@code InsuranceLine.extendData}
     * 落事件流。本类是该载荷的<b>键契约</b>：出单侧按此写入，出单 Saga 提取核保要素时按此解析，
     * 键名漂移即编译期可见。
     * </p>
     * <p>
     * 🔴 <b>健康告知「全或全无」口径</b>：核保域 {@code HealthDeclaration} 对身高/体重有强校验
     * （必须为正），且吸烟项是无三态的原始 {@code boolean}——故本侧声明了告知就必须同时提供
     * {@link #HEALTH_DECLARATION_SMOKING 吸烟}/{@link #HEALTH_DECLARATION_HEIGHT_CM 身高}/
     * {@link #HEALTH_DECLARATION_WEIGHT_KG 体重} 三个必答项，缺任一即视为<b>未提供</b>
     * （Saga 留痕告警、不代填），<b>禁止</b>以「不吸烟 / 默认身高体重」代填——
     * 静默代填会把「没告知」变成「低风险告知」。
     * </p>
     */
    public static class LineExtendDataKey {

        /** 健康告知块（嵌套对象，含既往病史/家族病史/吸烟/身高/体重五键及自定义告知项答案键） */
        public static final String HEALTH_DECLARATION = "healthDeclaration";

        /** 既往病史（字符串数组，无则空数组） */
        public static final String HEALTH_DECLARATION_MEDICAL_HISTORY = "medicalHistory";

        /** 家族遗传病史（字符串数组，无则空数组） */
        public static final String HEALTH_DECLARATION_FAMILY_HISTORY = "familyHistory";

        /** 是否吸烟（必答，布尔） */
        public static final String HEALTH_DECLARATION_SMOKING = "smoking";

        /** 身高厘米（必答，正数） */
        public static final String HEALTH_DECLARATION_HEIGHT_CM = "heightCm";

        /** 体重千克（必答，正数） */
        public static final String HEALTH_DECLARATION_WEIGHT_KG = "weightKg";

        /**
         * 自定义告知项答案（G12/g12-02）：嵌套对象，键=告知问题编码（metadata
         * {@code HealthDeclarationQuestion} 常量名，如 {@code HOSPITALIZATION_TWO_YEARS}），值=布尔。
         * <p>
         * 仅对产品「告知项清单」（{@code InsureCondition.healthDeclarationItems}）配置的问题作答，
         * 与上方五个平铺固定项<b>并列共存</b>——固定项是核保域强类型要素（吸烟/身高/体重等），
         * 本键是产品可配的扩展项（答什么由产品清单决定，本侧按清单核对）。
         * </p>
         */
        public static final String HEALTH_DECLARATION_ANSWERS = "answers";
    }
}
