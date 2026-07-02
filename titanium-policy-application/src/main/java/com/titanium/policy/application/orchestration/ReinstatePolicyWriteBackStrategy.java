package com.titanium.policy.application.orchestration;

import org.axonframework.commandhandling.gateway.CommandGateway;
import org.springframework.stereotype.Component;

import com.titanium.policy.command.ReinstatePolicyCommand;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 保单复效保全回写策略：保全类型 POLICY_REINSTATEMENT → 下发复效保单命令
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReinstatePolicyWriteBackStrategy implements MaintenanceWriteBackStrategy {

    private final CommandGateway commandGateway;

    @Override
    public String supportedType() {
        return "POLICY_REINSTATEMENT";
    }

    @Override
    public void writeBack(MaintenanceWriteBackContext context) {
        log.info("保全回写-复效保单, policyId={}", context.policyId());
        commandGateway.sendAndWait(new ReinstatePolicyCommand(context.policyId(), context.reason(),
                context.operatorId(), context.tenantId()));
    }
}
