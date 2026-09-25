package com.titanium.policy.web.controller.policy;


import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.titanium.common.context.RequestContextHolder;
import com.titanium.metadata.errorcode.PolicyErrorCode;
import com.titanium.metadata.exception.DomainException;
import com.titanium.policy.api.response.policy.PolicyStatisticsResponse;
import com.titanium.policy.application.command.policy.PolicyApplicationService;
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
import com.titanium.policy.web.dto.issuance.CreatePolicyDTO;
import com.titanium.policy.web.dto.maintenance.ApplyEndorsementDTO;
import com.titanium.policy.web.dto.maintenance.DistributeDividendDTO;
import com.titanium.policy.web.dto.maintenance.MaturePolicyDTO;
import com.titanium.policy.web.dto.maintenance.StartAnnuityPayoutDTO;
import com.titanium.policy.web.dto.maintenance.TerminatePolicyDTO;
import com.titanium.policy.web.dto.maintenance.WaivePremiumDTO;
import com.titanium.policy.web.dto.policy.PolicyReasonDTO;
import com.titanium.policy.web.handler.PolicyExceptionHandler;
import com.titanium.policy.web.mapper.PolicyStatisticsWebMapper;
import com.titanium.policy.web.mapper.PolicyWebMapper;
import com.titanium.policy.web.response.policy.PolicyDetailVO;

import jakarta.validation.Valid;
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
     * @return 保单ID
     */
    @PostMapping
    public ResponseEntity<String> createPolicy(@Valid @RequestBody CreatePolicyDTO request) {
        String tenantId = RequestContextHolder.requireTenantId();
        // 协议转换：HTTP Request → 领域命令，收敛到同一应用层门面
        CreatePolicyCommand command = policyWebMapper.toCommand(request, tenantId);
        String policyId = policyApplicationService.createPolicy(command);
        return new ResponseEntity<>(policyId, HttpStatus.CREATED);
    }

    /**
     * 一步出单（直接创建并签发）
     *
     * @param request 创建保单请求
     * @return 保单ID
     */
    @PostMapping("/direct")
    public ResponseEntity<String> createPolicyDirectly(@Valid @RequestBody CreatePolicyDTO request) {
        String tenantId = RequestContextHolder.requireTenantId();
        CreatePolicyDirectlyCommand command = policyWebMapper.toDirectCommand(request, tenantId);
        String policyId = policyApplicationService.createPolicyDirectly(command);
        return new ResponseEntity<>(policyId, HttpStatus.CREATED);
    }

    /**
     * 获取保单详情
     * <p>
     * 🔴 未命中时抛 {@link DomainException} 而非返回空 body 的 404（R8-05）：读模型按
     * {@code policyId + tenantId} 查，查不到既可能是「保单不存在」也可能是「租户错配」，
     * 空 body 让调用方无从分辨；文案由 {@link PolicyExceptionHandler#notFoundInTenant} 统一给出，
     * 两种成因仍合并表述（分开报会成为探测 id 是否存在的侧信道）。
     * </p>
     *
     * @param policyId 保单ID
     * @return 保单详情
     */
    @GetMapping("/{policyId}")
    public ResponseEntity<PolicyDetailVO> getPolicy(@PathVariable String policyId) {
        String tenantId = RequestContextHolder.requireTenantId();
        return policyAppQueryService.findById(new FindPolicyByIdQuery(policyId, tenantId)).map(policyWebMapper::toVO)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> PolicyExceptionHandler.notFoundInTenant(PolicyErrorCode.POLICY_NOT_EXIST, policyId));
    }

    /**
     * 签发保单
     *
     * @param policyId 保单ID
     * @return 空响应
     */
    @PutMapping("/{policyId}/issue")
    public ResponseEntity<Void> issuePolicy(@PathVariable String policyId) {
        String operatorId = RequestContextHolder.requireOperatorId();
        String tenantId = RequestContextHolder.requireTenantId();
        policyApplicationService.issuePolicy(policyId, operatorId, tenantId);
        return ResponseEntity.noContent().build();
    }

    /**
     * 激活保单
     *
     * @param policyId 保单ID
     * @return 空响应
     */
    @PutMapping("/{policyId}/activate")
    public ResponseEntity<Void> activatePolicy(@PathVariable String policyId) {
        String tenantId = RequestContextHolder.requireTenantId();
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
     * @return 保单详情列表
     */
    @GetMapping
    public ResponseEntity<List<PolicyDetailVO>> listPolicies(
            @RequestParam(required = false) String policyNo,
            @RequestParam(required = false) String policyHolderName,
            @RequestParam(required = false) String insuredName,
            @RequestParam(required = false) String productCode,
            @RequestParam(required = false) String status,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime effectiveDateStart,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime effectiveDateEnd,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime expiryDateStart,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime expiryDateEnd,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        String tenantId = RequestContextHolder.requireTenantId();
        List<PolicyDetailVO> policies = policyAppQueryService
                .findByConditions(policyNo, policyHolderName, insuredName, productCode, status,
                        effectiveDateStart, effectiveDateEnd, expiryDateStart, expiryDateEnd, tenantId, page, size)
                .stream().map(policyWebMapper::toVO).toList();
        return ResponseEntity.ok(policies);
    }

    /**
     * 多条件分页查询保单，返回完整分页元数据。
     * <p>
     * 生效/止期四个区间端点（{@code effectiveDateStart/End}、{@code expiryDateStart/End}）取
     * ISO 日期时间串（如 {@code 2026-09-01T00:00:00}），由前端「高级搜索」的日期区间展开为两键传入；
     * 传空即不参与过滤。读侧谓词早已就绪（{@code PolicyQueryServiceImpl#buildSpecification}），
     * 此前断在本方法未声明这四个参数——下游 Spring 对未声明的查询参数**静默忽略**，
     * 表现为「加了条件，条数不变、无任何报错」。
     * </p>
     */
    @GetMapping("/page")
    public ResponseEntity<Page<PolicyDetailVO>> pagePolicies(
            @RequestParam(required = false) String policyNo,
            @RequestParam(required = false) String policyHolderName,
            @RequestParam(required = false) String insuredName,
            @RequestParam(required = false) String productCode,
            @RequestParam(required = false) String status,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime effectiveDateStart,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime effectiveDateEnd,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime expiryDateStart,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime expiryDateEnd,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        String tenantId = RequestContextHolder.requireTenantId();
        Page<PolicyDetailVO> policies = policyAppQueryService
                .findPageByConditions(policyNo, policyHolderName, insuredName, productCode, status,
                        effectiveDateStart, effectiveDateEnd, expiryDateStart, expiryDateEnd, tenantId, page, size)
                .map(policyWebMapper::toVO);
        return ResponseEntity.ok(policies);
    }

    /**
     * 保单统计（管理后台看板聚合）
     * <p>
     * 返回有效保单数、今日新增数、保单总数及按险种一级分类的保单数分布。强制按 {@code X-Tenant-Id} 租户隔离。
     * </p>
     *
     * @return 保单统计结果
     */
    @GetMapping("/statistics")
    public ResponseEntity<PolicyStatisticsResponse> getStatistics(
            ) {
        String tenantId = RequestContextHolder.requireTenantId();
        return ResponseEntity.ok(
                policyStatisticsWebMapper.toResponse(policyAppQueryService.getStatistics(tenantId)));
    }

    /**
     * 查询保单受益人列表
     *
     * @param policyId 保单ID
     * @return 受益人列表
     */
    @GetMapping("/{policyId}/beneficiaries")
    public ResponseEntity<List<PolicyBeneficiaryQueryResult>> listBeneficiaries(
            @PathVariable String policyId) {
        String tenantId = RequestContextHolder.requireTenantId();
        return ResponseEntity.ok(policyAppQueryService.findBeneficiaries(policyId, tenantId));
    }

    /**
     * 查询保单被保险人列表
     *
     * @param policyId 保单ID
     * @return 被保险人列表
     */
    @GetMapping("/{policyId}/insured-parties")
    public ResponseEntity<List<PolicyInsuredQueryResult>> listInsuredParties(
            @PathVariable String policyId) {
        String tenantId = RequestContextHolder.requireTenantId();
        return ResponseEntity.ok(policyAppQueryService.findInsuredParties(policyId, tenantId));
    }

    /**
     * 查询保单批改历史列表
     *
     * @param policyId 保单ID
     * @return 批改历史列表
     */
    @GetMapping("/{policyId}/endorsements")
    public ResponseEntity<List<PolicyEndorsementQueryResult>> listEndorsements(
            @PathVariable String policyId) {
        String tenantId = RequestContextHolder.requireTenantId();
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
            @PathVariable String policyId) {
        String tenantId = RequestContextHolder.requireTenantId();
        return ResponseEntity.ok(policyAppQueryService.findMaintenanceCaseReferences(policyId, tenantId));
    }

    /**
     * 中止保单
     *
     * @param policyId 保单ID
     * @param request 变更原因请求
     * @return 空响应
     */
    @PutMapping("/{policyId}/suspend")
    public ResponseEntity<Void> suspendPolicy(@PathVariable String policyId,
                                              @Valid @RequestBody PolicyReasonDTO request) {
        String operatorId = RequestContextHolder.requireOperatorId();
        String tenantId = RequestContextHolder.requireTenantId();
        policyApplicationService.suspendPolicy(policyId, request.getReason(), operatorId, tenantId);
        return ResponseEntity.noContent().build();
    }

    /**
     * 恢复保单
     *
     * @param policyId 保单ID
     * @param request 变更原因请求
     * @return 空响应
     */
    @PutMapping("/{policyId}/resume")
    public ResponseEntity<Void> resumePolicy(@PathVariable String policyId,
                                             @Valid @RequestBody PolicyReasonDTO request) {
        String operatorId = RequestContextHolder.requireOperatorId();
        String tenantId = RequestContextHolder.requireTenantId();
        policyApplicationService.resumePolicy(policyId, request.getReason(), operatorId, tenantId);
        return ResponseEntity.noContent().build();
    }

    /**
     * 终止/退保
     *
     * @param policyId 保单ID
     * @param request 终止请求（原因+终止原因分类）
     * @return 空响应
     */
    @PutMapping("/{policyId}/terminate")
    public ResponseEntity<Void> terminatePolicy(@PathVariable String policyId,
                                                @Valid @RequestBody TerminatePolicyDTO request) {
        String operatorId = RequestContextHolder.requireOperatorId();
        String tenantId = RequestContextHolder.requireTenantId();
        policyApplicationService.terminatePolicy(policyId, request.getReason(), operatorId,
                request.getTerminationReason(), tenantId);
        return ResponseEntity.noContent().build();
    }

    /**
     * 撤销保单（仅未生效保单）
     *
     * @param policyId 保单ID
     * @param request 变更原因请求
     * @return 空响应
     */
    @PutMapping("/{policyId}/cancel")
    public ResponseEntity<Void> cancelPolicy(@PathVariable String policyId,
                                             @Valid @RequestBody PolicyReasonDTO request) {
        String operatorId = RequestContextHolder.requireOperatorId();
        String tenantId = RequestContextHolder.requireTenantId();
        policyApplicationService.cancelPolicy(policyId, request.getReason(), operatorId, tenantId);
        return ResponseEntity.noContent().build();
    }

    /**
     * 保费豁免（寿险保费豁免条款）
     *
     * @param policyId 保单ID
     * @param request 保费豁免请求
     * @return 空响应
     */
    @PutMapping("/{policyId}/waive-premium")
    public ResponseEntity<Void> waivePremium(@PathVariable String policyId,
                                             @Valid @RequestBody WaivePremiumDTO request) {
        String operatorId = RequestContextHolder.requireOperatorId();
        String tenantId = RequestContextHolder.requireTenantId();
        WaivePremiumCommand command = policyWebMapper.toWaivePremiumCommand(request, policyId, operatorId, tenantId);
        policyApplicationService.waivePremium(command);
        return ResponseEntity.noContent().build();
    }

    /**
     * 红利派发（分红险年度红利处理）
     *
     * @param policyId 保单ID
     * @param request 红利派发请求
     * @return 空响应
     */
    @PostMapping("/{policyId}/dividend")
    public ResponseEntity<Void> distributeDividend(@PathVariable String policyId,
                                                   @Valid @RequestBody DistributeDividendDTO request) {
        String operatorId = RequestContextHolder.requireOperatorId();
        String tenantId = RequestContextHolder.requireTenantId();
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
     * @return 空响应
     */
    @PostMapping("/{policyId}/annuity-payout/start")
    public ResponseEntity<Void> startAnnuityPayout(@PathVariable String policyId,
                                                   @Valid @RequestBody StartAnnuityPayoutDTO request) {
        String operatorId = RequestContextHolder.requireOperatorId();
        String tenantId = RequestContextHolder.requireTenantId();
        StartAnnuityPayoutCommand command = policyWebMapper.toStartAnnuityPayoutCommand(request, policyId, operatorId,
                tenantId);
        policyApplicationService.startAnnuityPayout(command);
        return ResponseEntity.noContent().build();
    }

    /**
     * 给付一期年金（给付期内触发）
     *
     * @param policyId 保单ID
     * @return 空响应
     */
    @PostMapping("/{policyId}/annuity-payout/pay")
    public ResponseEntity<Void> payAnnuityBenefit(@PathVariable String policyId) {
        String operatorId = RequestContextHolder.requireOperatorId();
        String tenantId = RequestContextHolder.requireTenantId();
        policyApplicationService.payAnnuityBenefit(new PayAnnuityBenefitCommand(policyId, operatorId, tenantId));
        return ResponseEntity.noContent().build();
    }

    /**
     * 满期给付（两全险/生存给付型寿险）
     *
     * @param policyId 保单ID
     * @param request 满期给付请求
     * @return 空响应
     */
    @PutMapping("/{policyId}/mature")
    public ResponseEntity<Void> maturePolicy(@PathVariable String policyId,
                                             @Valid @RequestBody MaturePolicyDTO request) {
        String operatorId = RequestContextHolder.requireOperatorId();
        String tenantId = RequestContextHolder.requireTenantId();
        MaturePolicyCommand command = policyWebMapper.toMaturePolicyCommand(request, policyId, operatorId, tenantId);
        policyApplicationService.maturePolicy(command);
        return ResponseEntity.noContent().build();
    }

    /**
     * 申请保单批改（数据/要素类批改回写）
     *
     * @param policyId 保单ID
     * @param request 申请批改请求
     * @return 保单ID
     */
    @PostMapping("/{policyId}/endorsement")
    public ResponseEntity<String> applyEndorsement(@PathVariable String policyId,
                                                   @Valid @RequestBody ApplyEndorsementDTO request) {
        String operatorId = RequestContextHolder.requireOperatorId();
        String tenantId = RequestContextHolder.requireTenantId();
        ApplyPolicyEndorsementCommand command = policyWebMapper.toApplyEndorsementCommand(request, policyId, operatorId,
                tenantId);
        String result = policyApplicationService.applyEndorsement(command);
        return ResponseEntity.ok(result);
    }
}
