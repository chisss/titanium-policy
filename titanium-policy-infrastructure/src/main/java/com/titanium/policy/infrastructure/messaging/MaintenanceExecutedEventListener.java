package com.titanium.policy.infrastructure.messaging;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.alibaba.fastjson2.JSONObject;

import lombok.extern.slf4j.Slf4j;

/**
 * 保全执行事件监听器（policy 域防腐层）
 * <p>
 * 监听 maintenance 域发布的"保全执行完成"事件，按保全类型翻译为对应保单命令，完成
 * "保全审批执行 → 保单状态变更/批改"的回写闭环。policy 是变更的执行者，maintenance 是发起者。
 * </p>
 * <p>
 * 防腐设计：以原始 JSON 解析，<b>不依赖 maintenance 域任何类</b>，避免跨域耦合；保全类型编码
 * 作为两域约定的契约字符串。状态类型→命令的分发用策略注册表（多态）；数据/要素类批改回退到
 * 统一的批改处理器（{@link MaintenanceWriteBackHandler#supportedType()} == "ENDORSEMENT"）。
 * </p>
 */
@Slf4j
@Component
public class MaintenanceExecutedEventListener {

    /** 保全执行事件主题（与 maintenance 域 MaintenanceConstants.KafkaTopic.MAINTENANCE_EXECUTED 约定一致） */
    private static final String MAINTENANCE_EXECUTED_TOPIC = "maintenance-executed";

    /** 数据/要素类批改统一处理器的注册键 */
    private static final String ENDORSEMENT_HANDLER_KEY = "ENDORSEMENT";

    /** 保全类型编码 → 回写处理器 */
    private final Map<String, MaintenanceWriteBackHandler> handlerRegistry;

    public MaintenanceExecutedEventListener(List<MaintenanceWriteBackHandler> handlers) {
        this.handlerRegistry = handlers.stream()
                .collect(Collectors.toMap(MaintenanceWriteBackHandler::supportedType, Function.identity()));
        log.info("保全回写处理器已注册, 支持类型={}", handlerRegistry.keySet());
    }

    /**
     * 消费保全执行事件，翻译为保单命令
     *
     * @param payload 事件 JSON 报文
     */
    @KafkaListener(topics = MAINTENANCE_EXECUTED_TOPIC, groupId = "${spring.kafka.consumer.group-id}")
    public void onMaintenanceExecuted(String payload) {
        JSONObject json = JSONObject.parseObject(payload);
        String policyId = json.getString("policyId");
        String maintenanceType = json.getString("maintenanceType");
        String operatorId = json.getString("updatedBy");
        String reason = json.getString("executionDetails");
        String tenantId = json.getString("tenantId");
        // maintenanceId 序列化为嵌套对象 {"id":"..."}，需从内层取
        String sourceMaintenanceId = extractMaintenanceId(json);
        LocalDateTime effectiveTime = json.getObject("effectiveTime", LocalDateTime.class);

        if (policyId == null || maintenanceType == null) {
            log.warn("保全执行事件缺少 policyId/maintenanceType，跳过回写, payload={}", payload);
            return;
        }

        MaintenanceWriteBackContext context = new MaintenanceWriteBackContext(policyId, operatorId, reason, tenantId,
                maintenanceType, effectiveTime, sourceMaintenanceId);

        // 状态类保全：精确类型处理器（Suspend/Resume/Terminate/Reinstate）
        MaintenanceWriteBackHandler handler = handlerRegistry.get(maintenanceType);
        // 数据/要素类批改：回退到统一批改处理器
        if (handler == null
                && com.titanium.policy.valueobject.PolicyDataUpdateType.byMaintenanceType(maintenanceType) != null) {
            handler = handlerRegistry.get(ENDORSEMENT_HANDLER_KEY);
        }
        if (handler == null) {
            log.info("保全类型 {} 无匹配回写处理器，跳过, policyId={}", maintenanceType, policyId);
            return;
        }

        try {
            handler.handle(context);
            log.info("保全回写完成, type={}, policyId={}", maintenanceType, policyId);
        } catch (Exception e) {
            // 幂等保护：保单状态已达目标态（重复投递）等场景会抛业务异常，记录但不阻塞消费
            log.error("保全回写失败, type={}, policyId={}, 原因={}", maintenanceType, policyId, e.getMessage());
        }
    }

    private String extractMaintenanceId(JSONObject json) {
        JSONObject idObj = json.getJSONObject("maintenanceId");
        if (idObj != null) {
            return idObj.getString("id");
        }
        // 兼容 maintenanceId 直接为字符串的情形
        return json.getString("maintenanceId");
    }
}
