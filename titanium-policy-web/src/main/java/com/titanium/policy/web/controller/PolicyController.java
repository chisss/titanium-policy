package com.titanium.policy.web.controller;

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

import com.titanium.policy.application.command.PolicyApplicationService;
import com.titanium.policy.application.query.PolicyAppQueryService;
import com.titanium.policy.command.CreatePolicyCommand;
import com.titanium.policy.command.CreatePolicyDirectlyCommand;
import com.titanium.policy.query.query.FindPolicyByIdQuery;
import com.titanium.policy.web.mapper.PolicyWebMapper;
import com.titanium.policy.web.request.CreatePolicyRequest;
import com.titanium.policy.web.response.PolicyDetailVO;

import lombok.RequiredArgsConstructor;

/**
 * 保单控制器（后台/端上 HTTP 入口）
 * <p>
 * 面向管理后台/端上，路径 {@code /web/v1/policies}，入参 {@code CreatePolicyRequest}、出参
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

    /**
     * 创建保单（从投保单创建）
     *
     * @param request 创建保单请求
     * @param tenantId 租户ID
     * @return 保单ID
     */
    @PostMapping
    public ResponseEntity<String> createPolicy(@RequestBody CreatePolicyRequest request,
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
    public ResponseEntity<String> createPolicyDirectly(@RequestBody CreatePolicyRequest request,
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
}
