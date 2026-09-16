package com.titanium.policy.api;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import com.titanium.metadata.response.ApiResponse;
import com.titanium.policy.api.request.issuance.CreateProposalRequest;
import com.titanium.policy.api.response.issuance.ProposalResponse;

/**
 * 投保意向单聚合对外契约（Feign）
 * <p>
 * 命名主键为聚合根 {@code Proposal}，仅承载投保意向单聚合的远程调用；正式保单 {@code Policy}、
 * 投保单 {@code Insurance} 各自独立契约。契约路径遵从内部服务远程调用规约 {@code /api/v1/proposals}，
 * 由 web 层 {@code ProposalApiProvider} 实现，路径不得篡改。
 * </p>
 * <p>
 * 同域多个 {@code @FeignClient} 的 {@code name} 相同，必须各配唯一 {@code contextId}，
 * 否则 Spring 启动报「Multiple @FeignClient with the same name」Bean 冲突。
 * </p>
 * <p>
 * 🔴 <b>本契约是「意向单草稿」契约，不是出单契约</b>：{@link #createProposal} 的入参
 * {@link CreateProposalRequest} 只承载标量头字段，不承载参与方（投保人/被保险人/受益人）与标的，
 * 故经本契约创建的意向单<b>恒不可提交</b>（{@link #submitProposal} 前置为「≥1 申请人 + ≥1 标的」）。
 * 完整出单请走统一出单入口 {@code POST /api/v1/issuances}（{@code PolicyIssuanceApi}）。
 * 该边界与 {@code ProposalApplicationService} 标量重载上的同源注释一致。
 * </p>
 * <p>
 * 处置依据：台账 D-501-01（独立投保意向单入口恒不可提交）。是否需要让本契约具备完整创建能力，
 * 取决于「独立意向单入口是否应支持提交」的产品决策 —— 若需要，须补齐参与方/标的入参设计
 * （涉及证件号等 PII 的跨服务暴露面），并与统一出单入口划清边界；在此之前保持草稿定位。
 * </p>
 */
@FeignClient(name = "titanium-policy", contextId = "proposalApi", path = "/api/v1/proposals")
public interface ProposalApi {

    /**
     * 创建投保意向单（草稿级）
     * <p>
     * 仅落标量头字段，参与方与标的为空表；所创建意向单不可经 {@link #submitProposal} 提交。
     * </p>
     *
     * @param dto 创建投保意向单请求（草稿级，不含参与方与标的）
     * @param tenantId 租户ID
     * @return 意向单ID
     */
    @PostMapping
    ApiResponse<String> createProposal(@RequestBody CreateProposalRequest dto,
                                       @RequestHeader("X-Tenant-Id") String tenantId);

    /**
     * 获取投保意向单详情（跨域集成用）
     *
     * @param proposalId 意向单ID
     * @param tenantId 租户ID
     * @return 意向单详情，不存在时 code=404
     */
    @GetMapping("/{proposalId}")
    ApiResponse<ProposalResponse> getProposal(@PathVariable("proposalId") String proposalId,
                                         @RequestHeader("X-Tenant-Id") String tenantId);

    /**
     * 提交投保意向单
     * <p>
     * 🔴 <b>前置条件</b>：意向单须已有「至少一名申请人 + 至少一个标的」。
     * 经 {@link #createProposal} 创建的意向单两张清单恒为空表，且本契约未提供参与方/标的补录端点，
     * 故对该类意向单调用本端点会以 {@code POLICY_RULE_VIOLATION} 被拒 —— 属<b>预期行为</b>（可解释业务码，非 500），
     * 不是可重试的瞬时故障。
     * </p>
     * <p>
     * 若需「创建后即可提交」，请不要串联本契约的两个端点，改用统一出单入口
     * {@code POST /api/v1/issuances}（{@code PolicyIssuanceApi}）：它装配完整参与方与标的，
     * 并在出单编排中自动发 {@code SubmitProposalCommand}。
     * </p>
     *
     * @param proposalId 意向单ID
     * @param tenantId 租户ID
     * @return 空响应
     */
    @PutMapping("/{proposalId}/submit")
    ApiResponse<Void> submitProposal(@PathVariable("proposalId") String proposalId,
                                     @RequestHeader("X-Tenant-Id") String tenantId);
}
