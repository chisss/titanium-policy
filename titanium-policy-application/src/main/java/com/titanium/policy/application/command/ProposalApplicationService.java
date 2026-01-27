package com.titanium.policy.application.command;

import org.axonframework.commandhandling.gateway.CommandGateway;
import org.springframework.stereotype.Service;

import com.titanium.policy.command.CreateProposalCommand;
import com.titanium.policy.command.SubmitProposalCommand;

import jakarta.annotation.Resource;

/**
 * 投保意向单应用服务
 * <p>
 * 处理投保意向单相关的命令，协调领域层和基础设施层
 * </p>
 */
@Service
public class ProposalApplicationService {
    @Resource
    private CommandGateway commandGateway;

    /**
     * 创建投保意向单
     *
     * @param command 创建投保意向单命令
     * @return 意向单ID
     */
    public String createProposal(CreateProposalCommand command) {
        // 发送命令
        commandGateway.sendAndWait(command);
        return command.proposalId();
    }

    /**
     * 提交投保意向单
     *
     * @param command 提交投保意向单命令
     */
    public void submitProposal(SubmitProposalCommand command) {
        // 发送命令
        commandGateway.sendAndWait(command);
    }
}
