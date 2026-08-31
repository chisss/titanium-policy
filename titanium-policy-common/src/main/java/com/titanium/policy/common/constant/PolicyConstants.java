package com.titanium.policy.common.constant;

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
        public static final String POLICY_EVENTS = "titanium.policy.events"; // 保单事件主题
        public static final String POLICY_CREATED = "titanium.policy.created"; // 保单创建事件主题
        public static final String POLICY_ACTIVATED = "titanium.policy.activated"; // 保单激活事件主题
        public static final String POLICY_ISSUED = "titanium.policy.issued"; // 保单签发事件主题（供监管采集/自动分保消费）
        public static final String POLICY_EXPIRED = "titanium.policy.expired"; // 保单过期事件主题
        public static final String POLICY_CANCELLED = "titanium.policy.cancelled"; // 保单取消事件主题
    }
}
