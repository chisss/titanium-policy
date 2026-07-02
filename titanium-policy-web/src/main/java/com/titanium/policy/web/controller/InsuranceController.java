package com.titanium.policy.web.controller;

import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.titanium.policy.application.command.InsuranceApplicationService;
import com.titanium.policy.application.query.InsuranceAppQueryService;
import com.titanium.policy.web.mapper.InsuranceWebMapper;
import com.titanium.policy.web.request.ConvertToInsuranceRequest;
import com.titanium.policy.web.response.InsuranceVO;

import jakarta.annotation.Resource;

/**
 * 投保单控制器
 * <p>
 * 表现层仅依赖 Web 层 Request/VO 与应用层 command/query 服务，不直接依赖领域命令/聚合根： 写入口经
 * {@link InsuranceApplicationService}（应用层构造命令），读入口经 {@link InsuranceAppQueryService}
 * 查询读模型并由 {@link InsuranceWebMapper} 转 VO。
 * </p>
 */
@RestController
@RequestMapping("/api/insurances")
public class InsuranceController {

    @Resource
    private InsuranceApplicationService insuranceApplicationService;

    @Resource
    private InsuranceAppQueryService    insuranceAppQueryService;

    @Resource
    private InsuranceWebMapper          insuranceWebMapper;

    /**
     * 从投保意向单创建投保单
     *
     * @param request 转换请求
     * @param tenantId 租户ID
     * @return 响应结果
     */
    @PostMapping("/from-proposal")
    public ResponseEntity<String> convertFromProposal(@RequestBody ConvertToInsuranceRequest request,
                                                      @RequestHeader("X-Tenant-Id") String tenantId) {
        String insuranceId = insuranceApplicationService.convertFromProposal(request.getInsuranceId(),
                request.getInsuranceNo(), request.getProposalId(), request.getPolicyForm(), request.getApplicantId(),
                request.getInsuredCount(), request.getExactPremium(), request.getCurrency(),
                request.getInsurancePeriodStart(), request.getInsurancePeriodEnd(), request.getProductCodes(),
                request.getUnderwritingPriority(), request.getChangeReason(), tenantId);
        return new ResponseEntity<>(insuranceId, HttpStatus.CREATED);
    }

    /**
     * 获取投保单详情
     *
     * @param insuranceId 投保单ID
     * @param tenantId 租户ID
     * @return 投保单详情
     */
    @GetMapping("/{insuranceId}")
    public ResponseEntity<InsuranceVO> getInsurance(@PathVariable String insuranceId,
                                                    @RequestHeader("X-Tenant-Id") String tenantId) {
        Optional<InsuranceVO> vo = insuranceAppQueryService.findById(insuranceId, tenantId)
                .map(insuranceWebMapper::toVO);
        return vo.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * 提交核保
     *
     * @param insuranceId 投保单ID
     * @param tenantId 租户ID
     * @return 响应结果
     */
    @PutMapping("/{insuranceId}/underwriting")
    public ResponseEntity<Void> submitUnderwriting(@PathVariable String insuranceId,
                                                   @RequestHeader("X-Tenant-Id") String tenantId) {
        insuranceApplicationService.submitUnderwriting(insuranceId, tenantId);
        return ResponseEntity.noContent().build();
    }

    /**
     * 触发承保流程
     *
     * @param insuranceId 投保单ID
     * @param tenantId 租户ID
     * @return 响应结果
     */
    @PutMapping("/{insuranceId}/underwrite")
    public ResponseEntity<Void> triggerUnderwriting(@PathVariable String insuranceId,
                                                    @RequestHeader("X-Tenant-Id") String tenantId) {
        insuranceApplicationService.submitUnderwriting(insuranceId, tenantId);
        return ResponseEntity.noContent().build();
    }
}
