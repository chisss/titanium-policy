package com.titanium.policy.infrastructure.messaging.inbound;

import java.time.LocalDateTime;

/**
 * 保全执行事件入站消息（policy 域防腐镜像）
 * <p>
 * maintenance 域经 Kafka 外发的"保全执行完成"事件的 policy 域防腐镜像。<b>不依赖 maintenance 域任何类</b>，
 * 字段是两域约定的 JSON 报文结构，由 fastjson2 一次性反序列化（{@code parseObject(payload, 本类)}），
 * 取代手工逐字段 {@code getString} 与嵌套值对象提取。
 * </p>
 * <p>
 * <b>嵌套值对象契约</b>：发布侧值对象经 Jackson 序列化为嵌套对象，故 {@code maintenanceId} 对应
 * {@code {"id":"..."}}，以内嵌 {@link MaintenanceIdRef} 精确承接。
 * </p>
 */
public record MaintenanceExecutedMessage(MaintenanceIdRef maintenanceId, String policyId, String maintenanceType,
                                         LocalDateTime effectiveTime, String executionDetails, String updatedBy,
                                         String tenantId) {

    /** maintenance 域 MaintenanceId 值对象序列化形态 {@code {"id":"..."}} 的防腐镜像 */
    public record MaintenanceIdRef(String id) {
    }

    /** 空安全提取保全单号（maintenanceId 缺失时返回 null） */
    public String maintenanceIdValue() {
        return maintenanceId != null ? maintenanceId.id() : null;
    }
}
