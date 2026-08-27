package com.titanium.policy.web.controller;

import java.util.List;
import java.util.Optional;

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

import com.titanium.policy.application.command.InsuranceApplicationService;
import com.titanium.policy.application.query.InsuranceAppQueryService;
import com.titanium.policy.command.ConvertProposalToInsuranceCommand;
import com.titanium.policy.web.dto.ConvertToInsuranceDTO;
import com.titanium.policy.web.mapper.InsuranceWebMapper;
import com.titanium.policy.web.response.InsuranceVO;

import lombok.RequiredArgsConstructor;

/**
 * 投保单控制器
 * <p>
 * 面向后台/端上，路径 {@code /web/v1/insurances}，入参 {@code ConvertToInsuranceDTO}、出参
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
    public ResponseEntity<String> convertFromProposal(@RequestBody ConvertToInsuranceDTO request,
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
     * 多条件分页查询投保单（承保申请）列表
     * <p>
     * 面向管理后台组合检索：投保单编号（模糊）/投保人ID/主险产品ID/状态任意组合。
     * </p>
     *
     * @param insuranceNo 投保单编号（可空）
     * @param holderId    投保人客户ID
     * @param productId   主险产品ID
     * @param status      投保单状态（可空）
     * @param page        页码（从0开始）
     * @param size        每页条数
     * @param tenantId    租户ID
     * @return 投保单列表
     */
    @GetMapping
    public ResponseEntity<List<InsuranceVO>> listInsurances(
            @RequestParam(value = "insuranceNo", required = false) String insuranceNo,
            @RequestParam(value = "holderId", required = false) String holderId,
            @RequestParam(value = "productId", required = false) String productId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @RequestHeader("X-Tenant-Id") String tenantId) {
        List<InsuranceVO> insurances = insuranceAppQueryService
                .findByConditions(insuranceNo, holderId, productId, status, tenantId, page, size)
                .stream()
                .map(insuranceWebMapper::toVO)
                .toList();
        return ResponseEntity.ok(insurances);
    }

    /**
     * 多条件分页查询投保单，返回完整分页元数据。
     */
    @GetMapping("/page")
    public ResponseEntity<Page<InsuranceVO>> pageInsurances(
            @RequestParam(value = "insuranceNo", required = false) String insuranceNo,
            @RequestParam(value = "holderId", required = false) String holderId,
            @RequestParam(value = "productId", required = false) String productId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @RequestHeader("X-Tenant-Id") String tenantId) {
        Page<InsuranceVO> insurances = insuranceAppQueryService
                .findPageByConditions(insuranceNo, holderId, productId, status, tenantId, page, size)
                .map(insuranceWebMapper::toVO);
        return ResponseEntity.ok(insurances);
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
