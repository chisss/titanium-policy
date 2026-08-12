package com.titanium.policy.api;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import com.titanium.metadata.response.ApiResponse;
import com.titanium.policy.api.request.SubmitIssuanceRequest;
import com.titanium.policy.api.response.IssuanceResponse;

/**
 * 出单流程对外契约（Feign）
 * <p>
 * <b>统一出单入口</b>：调用方提交「产品 + 参与方 + 投保要素」，由系统依产品配置自动路由
 * 一步/两步/三步出单，无需感知内部步骤。这是 API 出单闭环的主入口。
 * </p>
 * <p>
 * 🔴 <b>命名取舍说明</b>：本契约的命名主键是「出单」这一<b>跨聚合流程用例</b>，而非单一聚合根
 * （规约 §3.4.10 要求「命名主键是聚合根」，针对的是聚合 CRUD 契约——{@link PolicyApi} /
 * {@link InsuranceApi} / {@link ProposalApi} 已遵从）。出单请求的产出物可能是意向单、投保单或
 * 正式保单三者之一（取决于产品配置的出单模式），强行归入任一聚合契约都会语义失真：
 * 挂 {@code PolicyApi} 下则三步出单场景并未产生 policy。故独立成契约。
 * </p>
 * <p>
 * 同域多个 {@code @FeignClient} 的 {@code name} 相同，必须各配唯一 {@code contextId}，
 * 否则 Spring 启动报「Multiple @FeignClient with the same name」Bean 冲突。
 * </p>
 */
@FeignClient(name = "titanium-policy", contextId = "policyIssuanceApi", path = "/api/v1/issuances")
public interface PolicyIssuanceApi {

    /**
     * 提交出单（产品驱动路由一/二/三步，调用方无需感知步数）
     * <p>
     * 处理链路：幂等校验 → 产品配置加载 → 投保要素校验（依产品条件裁决年龄/保额/职业/份额）→
     * 保费试算 → 出单模式路由 → 建单（意向单/投保单/保单）。两步与三步出单在建单后由 Saga
     * 事件驱动接力核保与承保。
     * </p>
     * <p>
     * <b>幂等</b>：同一 {@code bizNo} 重复提交返回首次受理结果，不产生第二张单据。
     * </p>
     *
     * @param request  出单请求（含结构化险种段方案 planLines）
     * @param tenantId 租户ID
     * @return 出单结果（含当前阶段、各单据ID、保费、支付凭据或拒绝原因）
     */
    @PostMapping
    ApiResponse<IssuanceResponse> submitIssuance(@RequestBody SubmitIssuanceRequest request,
                                                 @RequestHeader("X-Tenant-Id") String tenantId);

    /**
     * 查询出单进度（按业务流水号）
     * <p>
     * 两步/三步出单是异步长流程（含核保与收费），调用方经此接口轮询当前阶段与各单据ID。
     * </p>
     *
     * @param bizNo    业务流水号
     * @param tenantId 租户ID
     * @return 出单进度；流水号不存在时 data 为空
     */
    @GetMapping("/{bizNo}")
    ApiResponse<IssuanceResponse> getIssuanceProgress(@PathVariable("bizNo") String bizNo,
                                                      @RequestHeader("X-Tenant-Id") String tenantId);
}
