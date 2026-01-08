package com.titanium.policy.common.constant;

/**
 * 保单系统常量类
 */
public class PolicyConstants {
    
    /**
     * 保单状态常量
     */
    public static class PolicyStatus {
        public static final String PENDING = "PENDING"; // 待激活
        public static final String ACTIVE = "ACTIVE"; // 已激活
        public static final String EXPIRED = "EXPIRED"; // 已过期
        public static final String CANCELLED = "CANCELLED"; // 已取消
    }
    
    /**
     * 事件类型常量
     */
    public static class EventType {
        public static final String POLICY_CREATED = "POLICY_CREATED"; // 保单创建
        public static final String POLICY_ACTIVATED = "POLICY_ACTIVATED"; // 保单激活
        public static final String POLICY_EXPIRED = "POLICY_EXPIRED"; // 保单过期
        public static final String POLICY_CANCELLED = "POLICY_CANCELLED"; // 保单取消
    }
    
    /**
     * 消息队列主题常量
     */
    public static class KafkaTopic {
        public static final String POLICY_EVENTS = "titanium.policy.events"; // 保单事件主题
        public static final String POLICY_CREATED = "titanium.policy.created"; // 保单创建事件主题
        public static final String POLICY_ACTIVATED = "titanium.policy.activated"; // 保单激活事件主题
        public static final String POLICY_EXPIRED = "titanium.policy.expired"; // 保单过期事件主题
        public static final String POLICY_CANCELLED = "titanium.policy.cancelled"; // 保单取消事件主题
    }
}
