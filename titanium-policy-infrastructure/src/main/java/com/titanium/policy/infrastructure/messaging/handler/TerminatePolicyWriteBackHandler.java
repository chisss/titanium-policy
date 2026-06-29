package com.titanium.policy.infrastructure.messaging.handler;

import org.axonframework.commandhandling.gateway.CommandGateway;
import org.springframework.stereotype.Component;

import com.titanium.metadata.enums.policy.PolicyEnum;
import com.titanium.policy.command.TerminatePolicyCommand;
import com.titanium.policy.infrastructure.messaging.MaintenanceWriteBackContext;
import com.titanium.policy.infrastructure.messaging.MaintenanceWriteBackHandler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 保单终止保全回写处理器：保全类型 POLICY_TERMINATION → 下发终止保单命令
 * <p>
 * 保全驱动的终止通常为退保（WITHDRAWAL）。后续如需区分退保/合同解除等不同终止原因，
 * 可由保全事件携带终止原因码细化。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TerminatePolicyWriteBackHandler implements MaintenanceWriteBackHandler {

    private final CommandGateway commandGateway;

    @Override
    public String supportedType() {
        return "POLICY_TERMINATION";
    }

    @Override
    public void handle(MaintenanceWriteBackContext context) {
        log.info("保全回写-终止保单, policyId={}", context.policyId());
        commandGateway.sendAndWait(new TerminatePolicyCommand(context.policyId(), context.reason(),
                context.operatorId(), PolicyEnum.TerminationReason.WITHDRAWAL, context.tenantId()));
    }
}
