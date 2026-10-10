package com.titanium.policy.web.controller.policy;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.titanium.common.context.RequestContextHolder;
import com.titanium.metadata.response.ApiResponse;
import com.titanium.policy.application.command.policy.PolicyApplicationService;
import com.titanium.policy.command.AddInsuredMemberCommand;
import com.titanium.policy.command.RemoveInsuredMemberCommand;
import com.titanium.policy.web.dto.member.AddInsuredMemberDTO;
import com.titanium.policy.web.mapper.PolicyWebMapper;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 保单被保险人成员控制器（团单加保 / 家庭险增减员入口，G12/g12-06）
 * <p>
 * 面向管理后台/端上，路径 {@code /web/v1/policies/{policyId}/insured-members}，与
 * {@code PolicyController}（同资源根路径）平行收敛到同一 {@link PolicyApplicationService} 门面。
 * 🔴 <b>单独成类而非并入 PolicyController</b>：后者已 22 个端点，超
 * {@code FAT_CONTROLLER_BASELINE} 棘轮（只许下调），成员端点按资源子域细分落本类。
 * </p>
 * <p>
 * 职责仅「收请求 → 转命令 → 返回响应」，零业务规则：形态（团单/家庭险）与状态（生效）守卫、
 * 家庭关系约束由聚合根承担；读侧可见性经 {@code InsuredMemberAddedEvent} 投影补齐（最终一致）。
 * 增减员的业务规则口径（是否重新核保/退费/生效日衔接）待 R-4 业务确认，本入口只做通道不做规则。
 * </p>
 */
@RestController
@RequestMapping("/web/v1/policies")
@RequiredArgsConstructor
public class PolicyMemberController {

    private final PolicyApplicationService policyApplicationService;

    private final PolicyWebMapper          policyWebMapper;

    /**
     * 新增被保险人（团单加保 / 家庭险增员）
     *
     * @param policyId 保单ID
     * @param request 新增被保险人请求
     * @return 空响应
     */
    @PostMapping("/{policyId}/insured-members")
    public ResponseEntity<ApiResponse<Void>> addInsuredMember(@PathVariable String policyId,
                                                              @Valid @RequestBody AddInsuredMemberDTO request) {
        AddInsuredMemberCommand command = policyWebMapper.toAddInsuredMemberCommand(
                request, policyId, RequestContextHolder.requireOperatorId(), RequestContextHolder.requireTenantId());
        policyApplicationService.addInsuredMember(command);
        // 保留 201：创建语义属 HTTP 传输层，不并入 ApiResponse.code
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success());
    }

    /**
     * 移除被保险人（团单减保 / 家庭险减员）
     *
     * @param policyId 保单ID
     * @param insuredId 被移除的被保险人ID（取被保险人列表返回的 insuredId）
     * @param reason 移除原因（可空）
     * @return 空响应
     */
    @DeleteMapping("/{policyId}/insured-members/{insuredId}")
    public ApiResponse<Void> removeInsuredMember(@PathVariable String policyId,
                                                 @PathVariable String insuredId,
                                                 @RequestParam(required = false) String reason) {
        RemoveInsuredMemberCommand command = policyWebMapper.toRemoveInsuredMemberCommand(
                policyId, insuredId, reason, RequestContextHolder.requireOperatorId(),
                RequestContextHolder.requireTenantId());
        policyApplicationService.removeInsuredMember(command);
        return ApiResponse.success();
    }
}
