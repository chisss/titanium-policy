package com.titanium.policy.application.orchestration.maintenance;

import org.axonframework.commandhandling.gateway.CommandGateway;
import org.springframework.stereotype.Component;

import com.titanium.policy.command.SuspendPolicyCommand;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 保单暂停保全回写策略：保全类型 POLICY_SUSPENSION → 下发暂停保单命令
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SuspendPolicyWriteBackStrategy implements MaintenanceWriteBackStrategy {

    private final CommandGateway commandGateway;

    @Override
    public String supportedType() {
        return "POLICY_SUSPENSION";
    }

    @Override
    public void writeBack(MaintenanceWriteBackContext context) {
        log.info("保全回写-暂停保单, policyId={}", context.policyId());
        commandGateway.sendAndWait(new SuspendPolicyCommand(context.policyId(), context.reason(),
                context.operatorId(), context.tenantId()));
    }
}
