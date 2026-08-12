package com.titanium.policy.application.orchestration.maintenance.strategy;

import org.axonframework.commandhandling.gateway.CommandGateway;
import org.springframework.stereotype.Component;

import com.titanium.policy.application.orchestration.maintenance.context.MaintenanceWriteBackContext;
import com.titanium.policy.command.ResumePolicyCommand;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 保单恢复保全回写策略：保全类型 POLICY_RESUMPTION → 下发恢复保单命令
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ResumePolicyWriteBackStrategy implements MaintenanceWriteBackStrategy {

    private final CommandGateway commandGateway;

    @Override
    public String supportedType() {
        return "POLICY_RESUMPTION";
    }

    @Override
    public void writeBack(MaintenanceWriteBackContext context) {
        log.info("保全回写-恢复保单, policyId={}", context.policyId());
        commandGateway.sendAndWait(new ResumePolicyCommand(context.policyId(), context.reason(),
                context.operatorId(), context.tenantId()));
    }
}
