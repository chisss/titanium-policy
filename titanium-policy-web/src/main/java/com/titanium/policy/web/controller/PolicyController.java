package com.titanium.policy.web.controller;


import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.titanium.policy.api.response.PolicyStatisticsResponse;
import com.titanium.policy.application.command.PolicyApplicationService;
import com.titanium.policy.application.query.PolicyAppQueryService;
import com.titanium.policy.command.ApplyPolicyEndorsementCommand;
import com.titanium.policy.command.CreatePolicyCommand;
import com.titanium.policy.command.CreatePolicyDirectlyCommand;
import com.titanium.policy.command.DistributeDividendCommand;
import com.titanium.policy.command.MaturePolicyCommand;
import com.titanium.policy.command.PayAnnuityBenefitCommand;
import com.titanium.policy.command.StartAnnuityPayoutCommand;
import com.titanium.policy.command.WaivePremiumCommand;
import com.titanium.policy.query.query.FindPolicyByIdQuery;
import com.titanium.policy.query.result.PolicyBeneficiaryQueryResult;
import com.titanium.policy.query.result.PolicyEndorsementQueryResult;
import com.titanium.policy.query.result.PolicyInsuredQueryResult;
import com.titanium.policy.query.result.PolicyMaintenanceCaseReferenceQueryResult;
import com.titanium.policy.web.dto.ApplyEndorsementDTO;
import com.titanium.policy.web.dto.CreatePolicyDTO;
import com.titanium.policy.web.dto.DistributeDividendDTO;
import com.titanium.policy.web.dto.MaturePolicyDTO;
import com.titanium.policy.web.dto.PolicyReasonDTO;
import com.titanium.policy.web.dto.StartAnnuityPayoutDTO;
import com.titanium.policy.web.dto.TerminatePolicyDTO;
import com.titanium.policy.web.dto.WaivePremiumDTO;
import com.titanium.policy.web.mapper.PolicyStatisticsWebMapper;
import com.titanium.policy.web.mapper.PolicyWebMapper;
import com.titanium.policy.web.response.PolicyDetailVO;

import lombok.RequiredArgsConstructor;

/**
 * 保单控制器（后台/端上 HTTP 入口）
 * <p>
 * 面向管理后台/端上，路径 {@code /web/v1/policies}，入参 {@code CreatePolicyDTO}、出参
 * {@code PolicyDetailVO}，<b>不再 implements PolicyApi</b>（远程契约由
 * {@code PolicyApiProvider} 承接）。 表现层经 {@link PolicyWebMapper} 把 Request 转成 CQRS
 * 命令/查询：写入口构造 {@link CreatePolicyCommand} 交
 * {@link PolicyApplicationService}，读入口构造 {@link FindPolicyByIdQuery} 交
 * {@link PolicyAppQueryService} 查读模型并转 VO。web 可依赖 command/query，但不碰聚合根。与
 * {@code PolicyApiProvider} 平行收敛到同一应用层门面。
 * </p>
 */
@RestController
@RequestMapping("/web/v1/policies")
@RequiredArgsConstructor
public class PolicyController {

    private final PolicyApplicationService policyApplicationService;

    private final PolicyAppQueryService    policyAppQueryService;

    private final PolicyWebMapper          policyWebMapper;

    private final PolicyStatisticsWebMapper policyStatisticsWebMapper;

    /**
     * 创建保单（从投保单创建）
     *
     * @param request 创建保单请求
     * @param tenantId 租户ID
     * @return 保单ID
     */
    @PostMapping
    public ResponseEntity<String> createPolicy(@RequestBody CreatePolicyDTO request,
                                               @RequestHeader("X-Tenant-Id") String tenantId) {
        // 协议转换：HTTP Request → 领域命令，收敛到同一应用层门面
        CreatePolicyCommand command = policyWebMapper.toCommand(request, tenantId);
        String policyId = policyApplicationService.createPolicy(command);
        return new ResponseEntity<>(policyId, HttpStatus.CREATED);
    }

    /**
     * 一步出单（直接创建并签发）
     *
     * @param request 创建保单请求
     * @param tenantId 租户ID
     * @return 保单ID
     */
    @PostMapping("/direct")
    public ResponseEntity<String> createPolicyDirectly(@RequestBody CreatePolicyDTO request,
                                                       @RequestHeader("X-Tenant-Id") String tenantId) {
        CreatePolicyDirectlyCommand command = policyWebMapper.toDirectCommand(request, tenantId);
        String policyId = policyApplicationService.createPolicyDirectly(command);
        return new ResponseEntity<>(policyId, HttpStatus.CREATED);
    }

    /**
     * 获取保单详情
     *
     * @param policyId 保单ID
     * @param tenantId 租户ID
     * @return 保单详情
     */
    @GetMapping("/{policyId}")
    public ResponseEntity<PolicyDetailVO> getPolicy(@PathVariable("policyId") String policyId,
                                                    @RequestHeader("X-Tenant-Id") String tenantId) {
        return policyAppQueryService.findById(new FindPolicyByIdQuery(policyId, tenantId)).map(policyWebMapper::toVO)
                .map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * 签发保单
     *
     * @param policyId 保单ID
     * @param operatorId 操作人ID
     * @param tenantId 租户ID
     * @return 空响应
     */
    @PutMapping("/{policyId}/issue")
    public ResponseEntity<Void> issuePolicy(@PathVariable("policyId") String policyId,
                                            @RequestHeader("X-Operator-Id") String operatorId,
                                            @RequestHeader("X-Tenant-Id") String tenantId) {
        policyApplicationService.issuePolicy(policyId, operatorId, tenantId);
        return ResponseEntity.noContent().build();
    }

    /**
     * 激活保单
     *
     * @param policyId 保单ID
     * @param tenantId 租户ID
     * @return 空响应
     */
    @PutMapping("/{policyId}/activate")
    public ResponseEntity<Void> activatePolicy(@PathVariable("policyId") String policyId,
                                               @RequestHeader("X-Tenant-Id") String tenantId) {
        policyApplicationService.activatePolicy(policyId, tenantId);
        return ResponseEntity.noContent().build();
    }

    /**
     * 多条件分页查询保单
     *
     * @param policyNo 保单号（可空）
     * @param policyHolderName 投保人姓名（可空）
     * @param insuredName 被保险人姓名（可空）
     * @param productCode 产品编码（可空）
     * @param status 保单状态（可空）
     * @param page 页码（从0开始）
     * @param size 每页条数
     * @param tenantId 租户ID
     * @return 保单详情列表
     */
    @GetMapping
    public ResponseEntity<List<PolicyDetailVO>> listPolicies(
            @RequestParam(value = "policyNo", required = false) String policyNo,
            @RequestParam(value = "policyHolderName", required = false) String policyHolderName,
            @RequestParam(value = "insuredName", required = false) String insuredName,
            @RequestParam(value = "productCode", required = false) String productCode,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
            @RequestHeader("X-Tenant-Id") String tenantId) {
        List<PolicyDetailVO> policies = policyAppQueryService
                .findByConditions(policyNo, policyHolderName, insuredName, productCode, status, tenantId, page, size)
                .stream().map(policyWebMapper::toVO).toList();
        return ResponseEntity.ok(policies);
    }

    /**
     * 多条件分页查询保单，返回完整分页元数据。
     */
    @GetMapping("/page")
    public ResponseEntity<Page<PolicyDetailVO>> pagePolicies(
            @RequestParam(value = "policyNo", required = false) String policyNo,
            @RequestParam(value = "policyHolderName", required = false) String policyHolderName,
            @RequestParam(value = "insuredName", required = false) String insuredName,
            @RequestParam(value = "productCode", required = false) String productCode,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
            @RequestHeader("X-Tenant-Id") String tenantId) {
        Page<PolicyDetailVO> policies = policyAppQueryService
                .findPageByConditions(policyNo, policyHolderName, insuredName, productCode, status, tenantId, page, size)
                .map(policyWebMapper::toVO);
        return ResponseEntity.ok(policies);
    }

    /**
     * 查询保单受益人列表
     *
     * @param policyId 保单ID
     * @param tenantId 租户ID
     * @return 受益人列表
     */
    /**
     * 保单统计（管理后台看板聚合）
     * <p>
     * 返回有效保单数、今日新增数、保单总数及按险种一级分类的保单数分布。强制按 {@code X-Tenant-Id} 租户隔离。
     * </p>
     *
     * @param tenantId 租户ID
     * @return 保单统计结果
     */
    @GetMapping("/statistics")
    public ResponseEntity<PolicyStatisticsResponse> getStatistics(
            @RequestHeader("X-Tenant-Id") String tenantId) {
        return ResponseEntity.ok(
                policyStatisticsWebMapper.toResponse(policyAppQueryService.getStatistics(tenantId)));
    }

    @GetMapping("/{policyId}/beneficiaries")
    public ResponseEntity<List<PolicyBeneficiaryQueryResult>> listBeneficiaries(
            @PathVariable("policyId") String policyId,
            @RequestHeader("X-Tenant-Id") String tenantId) {
        return ResponseEntity.ok(policyAppQueryService.findBeneficiaries(policyId, tenantId));
    }

    /**
     * 查询保单被保险人列表
     *
     * @param policyId 保单ID
     * @param tenantId 租户ID
     * @return 被保险人列表
     */
    @GetMapping("/{policyId}/insured-parties")
    public ResponseEntity<List<PolicyInsuredQueryResult>> listInsuredParties(
            @PathVariable("policyId") String policyId,
            @RequestHeader("X-Tenant-Id") String tenantId) {
        return ResponseEntity.ok(policyAppQueryService.findInsuredParties(policyId, tenantId));
    }

    /**
     * 查询保单批改历史列表
     *
     * @param policyId 保单ID
     * @param tenantId 租户ID
     * @return 批改历史列表
     */
    @GetMapping("/{policyId}/endorsements")
    public ResponseEntity<List<PolicyEndorsementQueryResult>> listEndorsements(
            @PathVariable("policyId") String policyId,
            @RequestHeader("X-Tenant-Id") String tenantId) {
        return ResponseEntity.ok(policyAppQueryService.findEndorsements(policyId, tenantId));
    }

    /**
     * 查询已在保单生效的保全案件引用。
     * <p>
     * 本端点只读且不提供建案入口；在途保全仍由保全管理页面负责。
     * </p>
     */
    @GetMapping("/{policyId}/maintenance-cases")
    public ResponseEntity<List<PolicyMaintenanceCaseReferenceQueryResult>> listMaintenanceCaseReferences(
            @PathVariable("policyId") String policyId,
            @RequestHeader("X-Tenant-Id") String tenantId) {
        return ResponseEntity.ok(policyAppQueryService.findMaintenanceCaseReferences(policyId, tenantId));
    }

    /**
     * 中止保单
     *
     * @param policyId 保单ID
     * @param request 变更原因请求
     * @param operatorId 操作人ID
     * @param tenantId 租户ID
     * @return 空响应
     */
    @PutMapping("/{policyId}/suspend")
    public ResponseEntity<Void> suspendPolicy(@PathVariable("policyId") String policyId,
                                              @RequestBody PolicyReasonDTO request,
                                              @RequestHeader("X-Operator-Id") String operatorId,
                                              @RequestHeader("X-Tenant-Id") String tenantId) {
        policyApplicationService.suspendPolicy(policyId, request.getReason(), operatorId, tenantId);
        return ResponseEntity.noContent().build();
    }

    /**
     * 恢复保单
     *
     * @param policyId 保单ID
     * @param request 变更原因请求
     * @param operatorId 操作人ID
     * @param tenantId 租户ID
     * @return 空响应
     */
    @PutMapping("/{policyId}/resume")
    public ResponseEntity<Void> resumePolicy(@PathVariable("policyId") String policyId,
                                             @RequestBody PolicyReasonDTO request,
                                             @RequestHeader("X-Operator-Id") String operatorId,
                                             @RequestHeader("X-Tenant-Id") String tenantId) {
        policyApplicationService.resumePolicy(policyId, request.getReason(), operatorId, tenantId);
        return ResponseEntity.noContent().build();
    }

    /**
     * 终止/退保
     *
     * @param policyId 保单ID
     * @param request 终止请求（原因+终止原因分类）
     * @param operatorId 操作人ID
     * @param tenantId 租户ID
     * @return 空响应
     */
    @PutMapping("/{policyId}/terminate")
    public ResponseEntity<Void> terminatePolicy(@PathVariable("policyId") String policyId,
                                                @RequestBody TerminatePolicyDTO request,
                                                @RequestHeader("X-Operator-Id") String operatorId,
                                                @RequestHeader("X-Tenant-Id") String tenantId) {
        policyApplicationService.terminatePolicy(policyId, request.getReason(), operatorId,
                request.getTerminationReason(), tenantId);
        return ResponseEntity.noContent().build();
    }

    /**
     * 撤销保单（仅未生效保单）
     *
     * @param policyId 保单ID
     * @param request 变更原因请求
     * @param operatorId 操作人ID
     * @param tenantId 租户ID
     * @return 空响应
     */
    @PutMapping("/{policyId}/cancel")
    public ResponseEntity<Void> cancelPolicy(@PathVariable("policyId") String policyId,
                                             @RequestBody PolicyReasonDTO request,
                                             @RequestHeader("X-Operator-Id") String operatorId,
                                             @RequestHeader("X-Tenant-Id") String tenantId) {
        policyApplicationService.cancelPolicy(policyId, request.getReason(), operatorId, tenantId);
        return ResponseEntity.noContent().build();
    }

    /**
     * 保费豁免（寿险保费豁免条款）
     *
     * @param policyId 保单ID
     * @param request 保费豁免请求
     * @param operatorId 操作人ID
     * @param tenantId 租户ID
     * @return 空响应
     */
    @PutMapping("/{policyId}/waive-premium")
    public ResponseEntity<Void> waivePremium(@PathVariable("policyId") String policyId,
                                             @RequestBody WaivePremiumDTO request,
                                             @RequestHeader("X-Operator-Id") String operatorId,
                                             @RequestHeader("X-Tenant-Id") String tenantId) {
        WaivePremiumCommand command = policyWebMapper.toWaivePremiumCommand(request, policyId, operatorId, tenantId);
        policyApplicationService.waivePremium(command);
        return ResponseEntity.noContent().build();
    }

    /**
     * 红利派发（分红险年度红利处理）
     *
     * @param policyId 保单ID
     * @param request 红利派发请求
     * @param operatorId 操作人ID
     * @param tenantId 租户ID
     * @return 空响应
     */
    @PostMapping("/{policyId}/dividend")
    public ResponseEntity<Void> distributeDividend(@PathVariable("policyId") String policyId,
                                                   @RequestBody DistributeDividendDTO request,
                                                   @RequestHeader("X-Operator-Id") String operatorId,
                                                   @RequestHeader("X-Tenant-Id") String tenantId) {
        DistributeDividendCommand command = policyWebMapper.toDistributeDividendCommand(request, policyId, operatorId,
                tenantId);
        policyApplicationService.distributeDividend(command);
        return ResponseEntity.noContent().build();
    }

    /**
     * 启动年金给付期（年金险专属）
     *
     * @param policyId 保单ID
     * @param request 启动年金给付请求
     * @param operatorId 操作人ID
     * @param tenantId 租户ID
     * @return 空响应
     */
    @PostMapping("/{policyId}/annuity-payout/start")
    public ResponseEntity<Void> startAnnuityPayout(@PathVariable("policyId") String policyId,
                                                   @RequestBody StartAnnuityPayoutDTO request,
                                                   @RequestHeader("X-Operator-Id") String operatorId,
                                                   @RequestHeader("X-Tenant-Id") String tenantId) {
        StartAnnuityPayoutCommand command = policyWebMapper.toStartAnnuityPayoutCommand(request, policyId, operatorId,
                tenantId);
        policyApplicationService.startAnnuityPayout(command);
        return ResponseEntity.noContent().build();
    }

    /**
     * 给付一期年金（给付期内触发）
     *
     * @param policyId 保单ID
     * @param operatorId 操作人ID
     * @param tenantId 租户ID
     * @return 空响应
     */
    @PostMapping("/{policyId}/annuity-payout/pay")
    public ResponseEntity<Void> payAnnuityBenefit(@PathVariable("policyId") String policyId,
                                                  @RequestHeader("X-Operator-Id") String operatorId,
                                                  @RequestHeader("X-Tenant-Id") String tenantId) {
        policyApplicationService.payAnnuityBenefit(new PayAnnuityBenefitCommand(policyId, operatorId, tenantId));
        return ResponseEntity.noContent().build();
    }

    /**
     * 满期给付（两全险/生存给付型寿险）
     *
     * @param policyId 保单ID
     * @param request 满期给付请求
     * @param operatorId 操作人ID
     * @param tenantId 租户ID
     * @return 空响应
     */
    @PutMapping("/{policyId}/mature")
    public ResponseEntity<Void> maturePolicy(@PathVariable("policyId") String policyId,
                                             @RequestBody MaturePolicyDTO request,
                                             @RequestHeader("X-Operator-Id") String operatorId,
                                             @RequestHeader("X-Tenant-Id") String tenantId) {
        MaturePolicyCommand command = policyWebMapper.toMaturePolicyCommand(request, policyId, operatorId, tenantId);
        policyApplicationService.maturePolicy(command);
        return ResponseEntity.noContent().build();
    }

    /**
     * 申请保单批改（数据/要素类批改回写）
     *
     * @param policyId 保单ID
     * @param request 申请批改请求
     * @param operatorId 操作人ID
     * @param tenantId 租户ID
     * @return 保单ID
     */
    @PostMapping("/{policyId}/endorsement")
    public ResponseEntity<String> applyEndorsement(@PathVariable("policyId") String policyId,
                                                   @RequestBody ApplyEndorsementDTO request,
                                                   @RequestHeader("X-Operator-Id") String operatorId,
                                                   @RequestHeader("X-Tenant-Id") String tenantId) {
        ApplyPolicyEndorsementCommand command = policyWebMapper.toApplyEndorsementCommand(request, policyId, operatorId,
                tenantId);
        String result = policyApplicationService.applyEndorsement(command);
        return ResponseEntity.ok(result);
    }
}
