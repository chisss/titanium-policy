package com.titanium.policy.application.orchestration.maintenance.strategy;

import org.axonframework.commandhandling.gateway.CommandGateway;
import org.springframework.stereotype.Component;

import com.titanium.metadata.enums.policy.PolicyEnum;
import com.titanium.policy.application.orchestration.maintenance.context.MaintenanceWriteBackContext;
import com.titanium.policy.command.TerminatePolicyCommand;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 保单终止保全回写策略：保全类型 POLICY_TERMINATION → 下发终止保单命令（退保）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TerminatePolicyWriteBackStrategy implements MaintenanceWriteBackStrategy {

    private final CommandGateway commandGateway;

    @Override
    public String supportedType() {
        return "POLICY_TERMINATION";
    }

    @Override
    public void writeBack(MaintenanceWriteBackContext context) {
        log.info("保全回写-终止保单, policyId={}", context.policyId());
        commandGateway.sendAndWait(new TerminatePolicyCommand(context.policyId(), context.reason(),
                context.operatorId(), PolicyEnum.TerminationReason.WITHDRAWAL, context.tenantId()));
    }
}
