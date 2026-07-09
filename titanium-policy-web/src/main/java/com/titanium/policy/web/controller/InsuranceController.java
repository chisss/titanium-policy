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
import com.titanium.policy.command.ConvertProposalToInsuranceCommand;
import com.titanium.policy.web.mapper.InsuranceWebMapper;
import com.titanium.policy.web.request.ConvertToInsuranceRequest;
import com.titanium.policy.web.response.InsuranceVO;

import lombok.RequiredArgsConstructor;

/**
 * 投保单控制器
 * <p>
 * 面向后台/端上，路径 {@code /web/v1/insurances}，入参 {@code ConvertToInsuranceRequest}、出参
 * {@code InsuranceVO}，<b>不 implements InsuranceApi</b>（远程契约由
 * {@code InsuranceApiProvider} 承接）。 表现层经 {@link InsuranceWebMapper} 把 Request
 * 直接转成领域命令 {@link ConvertProposalToInsuranceCommand} 交
 * {@link InsuranceApplicationService}，读入口经 {@link InsuranceAppQueryService}
 * 查读模型并转 VO。 web 可依赖 command/query，但不碰聚合根。与 {@code InsuranceApiProvider}
 * 平行收敛到同一应用层门面。
 * </p>
 */
@RestController
@RequestMapping("/web/v1/insurances")
@RequiredArgsConstructor
public class InsuranceController {

    private final InsuranceApplicationService insuranceApplicationService;

    private final InsuranceAppQueryService    insuranceAppQueryService;

    private final InsuranceWebMapper          insuranceWebMapper;

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
        // 协议转换：HTTP Request → 领域命令，收敛到同一应用层门面
        ConvertProposalToInsuranceCommand command = insuranceWebMapper.toCommand(request, tenantId);
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
