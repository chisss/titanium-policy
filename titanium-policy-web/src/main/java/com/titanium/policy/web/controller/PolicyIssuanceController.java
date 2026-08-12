package com.titanium.policy.web.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.titanium.policy.api.request.SubmitIssuanceRequest;
import com.titanium.policy.api.response.IssuanceResponse;
import com.titanium.policy.application.command.PolicyIssuanceApplicationService;
import com.titanium.policy.valueobject.IssuanceRequest;
import com.titanium.policy.valueobject.IssuanceResult;
import com.titanium.policy.web.assembler.IssuanceRequestAssembler;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 出单流程控制器（面向后台与端上）
 * <p>
 * 与 {@code PolicyIssuanceApiProvider}（面向其它微服务）平行，二者收敛到同一应用层门面
 * {@link PolicyIssuanceApplicationService}，互不依赖。
 * </p>
 * <p>
 * 本控制器直接复用 api 层的 {@link SubmitIssuanceRequest} 作为入参：出单请求结构复杂
 * （嵌套方案行与标的），前后端与服务间契约完全一致，另建一份 DTO 只会产生等价重复。
 * 规约「web 用 DTO」的意图是隔离两类消费者的<b>差异</b>，此处无差异故不强制分裂。
 * </p>
 */
@Slf4j
@Tag(name = "出单流程", description = "统一出单入口：产品驱动路由一/二/三步出单")
@RestController
@RequestMapping("/web/v1/issuances")
@RequiredArgsConstructor
public class PolicyIssuanceController {

    private final PolicyIssuanceApplicationService policyIssuanceApplicationService;
    private final IssuanceRequestAssembler         issuanceRequestAssembler;

    /**
     * 提交出单
     * <p>
     * 出单模式由主险产品配置决定，调用方无需指定步数。同一 {@code bizNo} 重复提交返回首次结果。
     * </p>
     *
     * @param request  出单请求
     * @param tenantId 租户ID
     * @return 出单结果（受理失败时 HTTP 200 + success=false + 拒绝原因，业务失败非传输失败）
     */
    @Operation(summary = "提交出单", description = "产品驱动路由一/二/三步出单，支持一单多险")
    @PostMapping
    public ResponseEntity<IssuanceResponse> submitIssuance(@Valid @RequestBody SubmitIssuanceRequest request,
                                                          @RequestHeader("X-Tenant-Id") String tenantId) {
        log.info("[出单] 收到出单请求: bizNo={}, 险种段数={}", request.getBizNo(),
                request.getPlanLines() != null ? request.getPlanLines().size() : 0);
        IssuanceRequest domainRequest = issuanceRequestAssembler.toDomainRequest(request, tenantId);
        IssuanceResult result = policyIssuanceApplicationService.submitIssuance(domainRequest);
        return ResponseEntity.ok(issuanceRequestAssembler.toResponse(result));
    }

    /**
     * 查询出单进度
     *
     * @param bizNo    业务流水号
     * @param tenantId 租户ID
     * @return 出单进度；流水号不存在时 204
     */
    @Operation(summary = "查询出单进度", description = "两步/三步出单为异步长流程，据此轮询当前阶段")
    @GetMapping("/{bizNo}")
    public ResponseEntity<IssuanceResponse> getIssuanceProgress(@PathVariable("bizNo") String bizNo,
                                                                @RequestHeader("X-Tenant-Id") String tenantId) {
        return policyIssuanceApplicationService.getIssuanceProgress(bizNo, tenantId)
                .map(issuanceRequestAssembler::toResponse)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }
}
