package com.titanium.policy.infrastructure.messaging.handler;

import org.axonframework.commandhandling.gateway.CommandGateway;
import org.springframework.stereotype.Component;

import com.titanium.policy.command.ReinstatePolicyCommand;
import com.titanium.policy.infrastructure.messaging.MaintenanceWriteBackContext;
import com.titanium.policy.infrastructure.messaging.MaintenanceWriteBackHandler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 保单复效保全回写处理器：保全类型 POLICY_REINSTATEMENT → 下发复效保单命令
 * <p>
 * 寿险失效保单经补缴保费+重新核保通过的复效保全，审批执行后回写为生效。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReinstatePolicyWriteBackHandler implements MaintenanceWriteBackHandler {

    private final CommandGateway commandGateway;

    @Override
    public String supportedType() {
        return "POLICY_REINSTATEMENT";
    }

    @Override
    public void handle(MaintenanceWriteBackContext context) {
        log.info("保全回写-复效保单, policyId={}", context.policyId());
        commandGateway.sendAndWait(new ReinstatePolicyCommand(context.policyId(), context.reason(),
                context.operatorId(), context.tenantId()));
    }
}
