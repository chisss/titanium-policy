package com.titanium.policy.application.command;

import org.axonframework.commandhandling.gateway.CommandGateway;
import org.springframework.stereotype.Service;

import com.titanium.policy.command.CreateProposalCommand;
import com.titanium.policy.command.SubmitProposalCommand;
import com.titanium.policy.command.VoidProposalCommand;

import jakarta.annotation.Resource;

/**
 * 投保意向单应用服务
 */
@Service
public class ProposalApplicationService {
    @Resource
    private CommandGateway commandGateway;

    /**
     * 创建投保意向单
     */
    public String createProposal(CreateProposalCommand command) {
        commandGateway.sendAndWait(command);
        return command.proposalId();
    }

    /**
     * 提交投保意向单
     */
    public void submitProposal(SubmitProposalCommand command) {
        commandGateway.sendAndWait(command);
    }

    /**
     * 作废投保意向单
     */
    public void voidProposal(VoidProposalCommand command) {
        commandGateway.sendAndWait(command);
    }
}
