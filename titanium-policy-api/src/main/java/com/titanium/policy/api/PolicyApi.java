package com.titanium.policy.api;

import java.util.List;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import com.titanium.metadata.response.ApiResponse;
import com.titanium.policy.api.request.AccountValueWriteBackRequest;
import com.titanium.policy.api.request.CreatePolicyRequest;
import com.titanium.policy.api.request.RecordPremiumCollectionRequest;
import com.titanium.policy.api.request.maintenance.ApplyPolicyMaintenanceRequest;
import com.titanium.policy.api.response.PolicyBeneficiaryResponse;
import com.titanium.policy.api.response.PolicyClauseResponse;
import com.titanium.policy.api.response.PolicyEndorsementResponse;
import com.titanium.policy.api.response.PolicyMaintenanceSnapshotResponse;
import com.titanium.policy.api.response.PolicyResponse;
import com.titanium.policy.api.response.PolicyStatusResponse;
import com.titanium.policy.api.response.maintenance.PolicyMaintenanceApplicationResponse;

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
            @PathVariable("policyId") String policyId,
            @Valid @RequestBody ApplyPolicyMaintenanceRequest request,
            @RequestHeader("X-Operator-Id") String operatorId,
            @RequestHeader("X-Tenant-Id") String tenantId);

    /**
     * 创建保单（从投保单创建）
     *
     * @param dto 创建保单 DTO
     * @param tenantId 租户ID
     * @return 保单ID
     */
    @PostMapping
    ApiResponse<String> createPolicy(@RequestBody CreatePolicyRequest dto,
                                     @RequestHeader("X-Tenant-Id") String tenantId);

    /**
     * 一步出单（直接创建并签发）
     *
     * @param dto 创建保单 DTO
     * @param tenantId 租户ID
     * @return 保单ID
     */
    @PostMapping("/direct")
    ApiResponse<String> createPolicyDirectly(@RequestBody CreatePolicyRequest dto,
                                             @RequestHeader("X-Tenant-Id") String tenantId);

    /**
     * 获取保单详情（跨域集成用）
     *
     * @param policyId 保单ID
     * @param tenantId 租户ID
     * @return 保单详情，不存在时 code=404
     */
    @GetMapping("/{policyId}")
    ApiResponse<PolicyResponse> getPolicy(@PathVariable("policyId") String policyId,
                                     @RequestHeader("X-Tenant-Id") String tenantId);

    /**
     * 查询保单状态（跨域集成用，返回保单原生状态码）
     *
     * @param policyId 保单ID
     * @param tenantId 租户ID
     * @return 保单状态，不存在时 code=404
     */
    @GetMapping("/{policyId}/status")
    ApiResponse<PolicyStatusResponse> getPolicyStatus(@PathVariable("policyId") String policyId,
                                                 @RequestHeader("X-Tenant-Id") String tenantId);

    /** 查询保全建案使用的不可变 Policy 基准快照。 */
    @GetMapping("/{policyId}/maintenance-snapshot")
    ApiResponse<PolicyMaintenanceSnapshotResponse> getMaintenanceSnapshot(
            @PathVariable("policyId") String policyId,
            @RequestHeader("X-Tenant-Id") String tenantId);

    /** 查询保单租户隔离的批改历史，供跨域影响分析取证。 */
    @GetMapping("/{policyId}/endorsements")
    ApiResponse<List<PolicyEndorsementResponse>> getEndorsements(
            @PathVariable("policyId") String policyId,
            @RequestHeader("X-Tenant-Id") String tenantId);

    /**
     * 签发保单
     *
     * @param policyId 保单ID
     * @param operatorId 操作人ID
     * @param tenantId 租户ID
     * @return 空响应
     */
    @PutMapping("/{policyId}/issue")
    ApiResponse<Void> issuePolicy(@PathVariable("policyId") String policyId,
                                  @RequestHeader("X-Operator-Id") String operatorId,
                                  @RequestHeader("X-Tenant-Id") String tenantId);

    /**
     * 激活保单
     *
     * @param policyId 保单ID
     * @param tenantId 租户ID
     * @return 空响应
     */
    @PutMapping("/{policyId}/activate")
    ApiResponse<Void> activatePolicy(@PathVariable("policyId") String policyId,
                                     @RequestHeader("X-Tenant-Id") String tenantId);

    /** Payment 或 Billing 回写已核验的保费收讫事实。 */
    @PostMapping("/{policyId}/collections")
    ApiResponse<Void> recordPremiumCollection(
            @PathVariable("policyId") String policyId,
            @Valid @RequestBody RecordPremiumCollectionRequest request,
            @RequestHeader("X-Operator-Id") String operatorId,
            @RequestHeader("X-Tenant-Id") String tenantId);

    /**
     * 回写投资账户价值（investment 域调用，投连/万能保单账户价值变更后回写）
     *
     * @param policyId 保单ID
     * @param dto 账户价值回写 DTO
     * @param tenantId 租户ID
     * @return 空响应
     */
    @PutMapping("/{policyId}/account-value")
    ApiResponse<Void> writeBackAccountValue(@PathVariable("policyId") String policyId,
                                            @RequestBody AccountValueWriteBackRequest dto,
                                            @RequestHeader("X-Tenant-Id") String tenantId);

    /**
     * 查询保单条款快照列表（claim 域责任校验 CLAIM-4 的条款定位来源）
     *
     * @param policyId 保单ID
     * @param tenantId 租户ID
     * @return 条款快照列表（无条款时为空列表）
     */
    @GetMapping("/{policyId}/clauses")
    ApiResponse<List<PolicyClauseResponse>> getPolicyClauses(@PathVariable("policyId") String policyId,
                                                             @RequestHeader("X-Tenant-Id") String tenantId);

    /**
     * 查询保单受益人主数据（claim 域身故给付 CLAIM-4 的受益人比对基准）
     *
     * @param policyId 保单ID
     * @param tenantId 租户ID
     * @return 受益人列表（按受益顺位升序，无受益人时为空列表）
     */
    @GetMapping("/{policyId}/beneficiaries")
    ApiResponse<List<PolicyBeneficiaryResponse>> getBeneficiaries(@PathVariable("policyId") String policyId,
                                                                  @RequestHeader("X-Tenant-Id") String tenantId);
}
