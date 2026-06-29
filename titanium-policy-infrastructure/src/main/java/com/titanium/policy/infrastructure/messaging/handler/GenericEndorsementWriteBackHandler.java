package com.titanium.policy.infrastructure.messaging.handler;

import java.time.LocalDateTime;

import org.axonframework.commandhandling.gateway.CommandGateway;
import org.springframework.stereotype.Component;

import com.titanium.policy.command.ApplyPolicyEndorsementCommand;
import com.titanium.policy.infrastructure.messaging.MaintenanceWriteBackContext;
import com.titanium.policy.infrastructure.messaging.MaintenanceWriteBackHandler;
import com.titanium.policy.service.EndorsementNoGenerator;
import com.titanium.policy.valueobject.PolicyDataUpdateType;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 通用批改回写处理器：覆盖全部数据/要素类保全类型
 * <p>
 * 经 {@link PolicyDataUpdateType#byMaintenanceType} 将 maintenance 保全类型映射为批改类型，
 * 生成批单号并下发 {@link ApplyPolicyEndorsementCommand}。首版各批改类型回写逻辑同构
 * （版本递增 + 批单留痕），故用单个处理器（KISS/YAGNI）；待 maintenance 事件 enrich 出
 * 字段级变更明细、各类型回写逻辑真正分化时再按需拆子类。
 * </p>
 * <p>
 * 注册键 "ENDORSEMENT" 由 {@code MaintenanceExecutedEventListener} 作批改回退分支命中，
 * 不对应单一 maintenance 类型。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GenericEndorsementWriteBackHandler implements MaintenanceWriteBackHandler {

    private final CommandGateway          commandGateway;
    private final EndorsementNoGenerator  endorsementNoGenerator;

    @Override
    public String supportedType() {
        return "ENDORSEMENT";
    }

    @Override
    public void handle(MaintenanceWriteBackContext context) {
        PolicyDataUpdateType updateType = PolicyDataUpdateType.byMaintenanceType(context.maintenanceType());
        if (updateType == null) {
            log.warn("保全类型 {} 非可批改类型，跳过批改回写, policyId={}", context.maintenanceType(), context.policyId());
            return;
        }
        String endorsementNo = endorsementNoGenerator.generate(context.policyId());
        LocalDateTime effectiveDate = context.effectiveTime() != null ? context.effectiveTime() : LocalDateTime.now();
        log.info("保全回写-批改, type={}, policyId={}, endorsementNo={}", updateType.getCode(), context.policyId(),
                endorsementNo);
        commandGateway.sendAndWait(new ApplyPolicyEndorsementCommand(context.policyId(), endorsementNo, updateType,
                effectiveDate, context.reason(), null, context.sourceMaintenanceId(), context.operatorId(),
                context.tenantId()));
    }
}
