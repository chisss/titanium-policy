package com.titanium.policy.api;

import java.util.List;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import com.titanium.metadata.response.ApiResponse;
import com.titanium.policy.api.request.issuance.CreatePolicyRequest;
import com.titanium.policy.api.request.maintenance.AccountValueWriteBackRequest;
import com.titanium.policy.api.request.maintenance.ApplyPolicyMaintenanceRequest;
import com.titanium.policy.api.request.maintenance.RecordPremiumCollectionRequest;
import com.titanium.policy.api.request.member.AddInsuredMemberRequest;
import com.titanium.policy.api.response.maintenance.PolicyEndorsementResponse;
import com.titanium.policy.api.response.maintenance.PolicyMaintenanceApplicationResponse;
import com.titanium.policy.api.response.maintenance.PolicyMaintenanceSnapshotResponse;
import com.titanium.policy.api.response.policy.PolicyBeneficiaryResponse;
import com.titanium.policy.api.response.policy.PolicyClauseResponse;
import com.titanium.policy.api.response.policy.PolicyResponse;
import com.titanium.policy.api.response.policy.PolicyStatusResponse;

import jakarta.validation.Valid;

/**
 * 保单聚合对外契约（Feign）
 * <p>
 * 命名主键为聚合根 {@code Policy}（非 policy 域），仅承载正式保单聚合的远程调用；投保单
 * {@code Insurance}、投保意向单 {@code Proposal} 各自独立契约。契约路径遵从内部服务远程
 * 调用规约 {@code /api/v1/policies}，由 web 层 {@code PolicyApiProvider} 实现，路径不得篡改。
 * </p>
 * <p>
 * 同域多个 {@code @FeignClient} 的 {@code name} 相同，必须各配唯一 {@code contextId}，
 * 否则 Spring 启动报「Multiple @FeignClient with the same name」Bean 冲突。
 * </p>
 */
@FeignClient(name = "titanium-policy", contextId = "policyApi", path = "/api/v1/policies")
public interface PolicyApi {

    /** 立即应用保全案件的结构化合同变更。 */
    @PostMapping("/{policyId}/maintenance-applications")
    ApiResponse<PolicyMaintenanceApplicationResponse> applyMaintenance(
            @PathVariable String policyId,
            @Valid @RequestBody ApplyPolicyMaintenanceRequest request);

    /**
     * 创建保单（从投保单创建）
     *
     * @param dto 创建保单 DTO
     * @return 保单ID
     */
    @PostMapping
    ApiResponse<String> createPolicy(@RequestBody CreatePolicyRequest dto);

    /**
     * 一步出单（直接创建并签发）
     *
     * @param dto 创建保单 DTO
     * @return 保单ID
     */
    @PostMapping("/direct")
    ApiResponse<String> createPolicyDirectly(@RequestBody CreatePolicyRequest dto);

    /**
     * 获取保单详情（跨域集成用）
     *
     * @param policyId 保单ID
     * @return 保单详情，不存在时 code=404
     */
    @GetMapping("/{policyId}")
    ApiResponse<PolicyResponse> getPolicy(@PathVariable String policyId);

    /**
     * 查询保单状态（跨域集成用，返回保单原生状态码）
     *
     * @param policyId 保单ID
     * @return 保单状态，不存在时 code=404
     */
    @GetMapping("/{policyId}/status")
    ApiResponse<PolicyStatusResponse> getPolicyStatus(@PathVariable String policyId);

    /** 查询保全建案使用的不可变 Policy 基准快照。 */
    @GetMapping("/{policyId}/maintenance-snapshot")
    ApiResponse<PolicyMaintenanceSnapshotResponse> getMaintenanceSnapshot(
            @PathVariable String policyId);

    /** 查询保单租户隔离的批改历史，供跨域影响分析取证。 */
    @GetMapping("/{policyId}/endorsements")
    ApiResponse<List<PolicyEndorsementResponse>> getEndorsements(
            @PathVariable String policyId);

    /**
     * 签发保单
     *
     * @param policyId 保单ID
     * @return 空响应
     */
    @PutMapping("/{policyId}/issue")
    ApiResponse<Void> issuePolicy(@PathVariable String policyId);

    /**
     * 激活保单
     *
     * @param policyId 保单ID
     * @return 空响应
     */
    @PutMapping("/{policyId}/activate")
    ApiResponse<Void> activatePolicy(@PathVariable String policyId);

    /** Payment 或 Billing 回写已核验的保费收讫事实。 */
    @PostMapping("/{policyId}/collections")
    ApiResponse<Void> recordPremiumCollection(
            @PathVariable String policyId,
            @Valid @RequestBody RecordPremiumCollectionRequest request);

    /**
     * 回写投资账户价值（investment 域调用，投连/万能保单账户价值变更后回写）
     *
     * @param policyId 保单ID
     * @param dto 账户价值回写 DTO
     * @return 空响应
     */
    @PutMapping("/{policyId}/account-value")
    ApiResponse<Void> writeBackAccountValue(@PathVariable String policyId,
                                            @RequestBody AccountValueWriteBackRequest dto);

    /**
     * 查询保单条款快照列表（claim 域责任校验 CLAIM-4 的条款定位来源）
     *
     * @param policyId 保单ID
     * @return 条款快照列表（无条款时为空列表）
     */
    @GetMapping("/{policyId}/clauses")
    ApiResponse<List<PolicyClauseResponse>> getPolicyClauses(@PathVariable String policyId);

    /**
     * 查询保单受益人主数据（claim 域身故给付 CLAIM-4 的受益人比对基准）
     *
     * @param policyId 保单ID
     * @return 受益人列表（按受益顺位升序，无受益人时为空列表）
     */
    @GetMapping("/{policyId}/beneficiaries")
    ApiResponse<List<PolicyBeneficiaryResponse>> getBeneficiaries(@PathVariable String policyId);

    /**
     * 新增被保险人（团单加保 / 家庭险增员，G12/g12-06）
     * <p>
     * 仅团单/家庭险的生效保单可增减成员（形态+状态守卫在聚合根）；家庭险须提供家庭成员关系码。
     * 读侧可见性经 {@code InsuredMemberAddedEvent} 投影补齐（最终一致）。
     * </p>
     *
     * @param policyId 保单ID
     * @param request 新增被保险人请求
     * @return 空响应
     */
    @PostMapping("/{policyId}/insured-members")
    ApiResponse<Void> addInsuredMember(@PathVariable String policyId,
                                       @Valid @RequestBody AddInsuredMemberRequest request);

    /**
     * 移除被保险人（团单减保 / 家庭险减员，G12/g12-06）
     *
     * @param policyId 保单ID
     * @param insuredId 被移除的被保险人ID（取被保险人列表返回的 insuredId）
     * @param reason 移除原因（可空，业务描述文案由调用方按枚举/常量提供）
     * @return 空响应
     */
    @DeleteMapping("/{policyId}/insured-members/{insuredId}")
    ApiResponse<Void> removeInsuredMember(@PathVariable String policyId, @PathVariable String insuredId,
                                          @RequestParam(required = false) String reason);
}
