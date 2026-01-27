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

import com.titanium.policy.aggregate.Insurance;
import com.titanium.policy.application.command.InsuranceApplicationService;
import com.titanium.policy.application.query.InsuranceAppQueryService;
import com.titanium.policy.command.ConvertProposalToInsuranceCommand;

import jakarta.annotation.Resource;

/**
 * 投保单控制器
 */
@RestController
@RequestMapping("/api/insurances")
public class InsuranceController {

    @Resource
    private InsuranceApplicationService insuranceApplicationService;

    @Resource
    private InsuranceAppQueryService    insuranceAppQueryService;

    /**
     * 从投保意向单创建投保单
     *
     * @param command 转换命令
     * @return 响应结果
     */
    @PostMapping("/from-proposal")
    public ResponseEntity<String> convertFromProposal(@RequestBody ConvertProposalToInsuranceCommand command) {
        String insuranceId = insuranceApplicationService.convertFromProposal(command);
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
    public ResponseEntity<Insurance> getInsurance(@PathVariable String insuranceId,
                                                  @RequestHeader("X-Tenant-Id") String tenantId) {
        Optional<Insurance> insurance = insuranceAppQueryService.findById(insuranceId, tenantId);
        return insurance.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
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
        insuranceApplicationService.triggerUnderwriting(insuranceId, tenantId);
        return ResponseEntity.noContent().build();
    }
}
