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
}
