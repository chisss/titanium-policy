package com.titanium.policy.infrastructure.adapter.underwriting;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import com.titanium.common.context.RequestContextHolder;
import com.titanium.common.exception.BusinessException;
import com.titanium.metadata.enums.underwriting.UnderwritingEnum;
import com.titanium.metadata.enums.underwriting.UnderwritingEnum.ConclusionType;
import com.titanium.policy.port.underwriting.UnderwritingDecisionGateway;
import com.titanium.policy.valueobject.insurance.UnderwritingDecisionRequest;
import com.titanium.policy.valueobject.insurance.UnderwritingResult;
import com.titanium.underwriting.api.UnderwritingApi;
import com.titanium.underwriting.api.request.underwriting.CreateUnderwritingRequest;
import com.titanium.underwriting.api.request.underwriting.DecideUnderwritingApiRequest;
import com.titanium.underwriting.api.request.underwriting.SubmitUnderwritingInputApiRequest;
import com.titanium.underwriting.api.response.underwriting.UnderwritingResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 核保决策网关同步适配器（富核保路径）
 * <p>
 * {@link UnderwritingDecisionGateway} 的当前阶段实现：注册中心/消息总线就绪前，直接经
 * {@link UnderwritingApi}（Feign）同步调用核保域，完成富核保「创建核保 → 提交结构化输入 → 触发决策 → 回传结论」。
 * 将核保域 {@link UnderwritingResponse} 翻译为保单域 {@link UnderwritingResult}，构成防腐层（ACL）。
 * </p>
 * <p>
 * <b>UW-2 富核保切换</b>：替代原「createUnderwriting + underwrite（金额>10万兜底）」路径，改走
 * submitInput（组装当前 API 支持的职业/BMI 风险要素）+ decide（触发核保域富评分决策）。
 * 被保人要素不足时留空；年龄/性别因当前核保 API 无对应字段，暂不在同步适配器中透传。
 * </p>
 * <p>
 * <b>UW-3 加费回传</b>：从决策后 DTO 读取结构化加费率 {@code extraPremiumRatio} 填入
 * {@link UnderwritingResult}，供出单 Saga 并入保费。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SyncUnderwritingDecisionAdapter implements UnderwritingDecisionGateway {

    /** 自动核保方式（出单主链路默认走自动核保决策） */
    private static final String AUDIT_TYPE_AUTOMATIC = "AUTOMATIC";

    /**
     * 系统主体标识：核保决策由本域自动发起，操作人是**系统**而非投保人。
     * <p>m21-03（审查报告 RPC-02）：改造前三处审计字段一律填 {@code holderId}，
     * 使核保记录显示「投保人本人提交了核保，并且本人做出了核保决策」——审计留痕失真
     * （NIST SP 800-53 AU-3/AU-10：审计记录须写明真实主体）。</p>
     */
    private static final String SYSTEM_OPERATOR = "SYSTEM_AUTO_UNDERWRITING";

    private final UnderwritingApi underwritingApi;

    @Override
    public UnderwritingResult requestDecision(UnderwritingDecisionRequest request) {
        log.info("[核保网关] 同步请求富核保, insuranceId={}, holderId={}, tenantId={}", request.insuranceId(),
                request.holderId(), request.tenantId());

        // 1. 幂等前置：先按投保单号查已有核保单，有则复用。
        //    🔴 m21-03（审查报告 RPC-02）：原实现每次调用都新建，Saga 重试或编排器重入一次即多一张
        //    孤儿核保单（三次远程写全无幂等键）。此处用「投保单号」这一稳定业务键做查重——
        //    underwriting 域已有 getUnderwritingByPolicyId 只读查询，无需改契约即可获得幂等。
        //    仍存的语义污染：本域把 insuranceId 写进对方 policyId 字段（跨域语义错配），
        //    根治需 underwriting 域提供粗粒度 :auto-decide 接口（见批次规划 §二 m21-03 备注）。
        String underwritingId = findExistingUnderwritingId(request);
        if (underwritingId == null) {
            // 创建核保单（透传险种编码供核保域按产品配置决策——UW-4）
            ResponseEntity<UnderwritingResponse> createdResponse = underwritingApi.createUnderwriting(
                    buildCreateRequest(request));
            UnderwritingResponse created = requireSuccessfulBody(createdResponse, "创建核保", request);
            underwritingId = requireUnderwritingId(created, "创建核保", request);
        } else {
            log.info("[核保网关] 命中已有核保单，跳过创建（幂等）: insuranceId={}, underwritingId={}",
                    request.insuranceId(), underwritingId);
        }

        // 2. 提交结构化核保输入（被保人风险要素 → 富核保评分依据），替代旧金额兜底
        ResponseEntity<UnderwritingResponse> submittedResponse = underwritingApi.submitInput(underwritingId,
                buildInputRequest(request));
        requireSuccessfulBody(submittedResponse, "提交核保输入", request);

        // 3. 触发核保决策（核保域基于已提交输入产出富结论/风险等级/加费）
        ResponseEntity<UnderwritingResponse> decidedResponse = underwritingApi.decide(underwritingId,
                buildDecideRequest(request));
        UnderwritingResponse decided = requireSuccessfulBody(decidedResponse, "触发核保决策", request);
        String decidedUnderwritingId = requireUnderwritingId(decided, "核保决策", request);

        // 4. 核保域结果翻译为保单域核保结果（防腐层），携带结构化加费率
        ConclusionType resultCode = resolveConclusion(decided, request);
        BigDecimal extraPremiumRatio = decided.getExtraPremiumRatio();
        log.info("[核保网关] 富核保完成, insuranceId={}, underwritingId={}, 结论={}, 加费率={}", request.insuranceId(),
                decidedUnderwritingId, resultCode, extraPremiumRatio);

        return new UnderwritingResult(decidedUnderwritingId, resultCode, decided.getReviewComments(),
                decided.getUnderwriterId(), LocalDateTime.now(), decided.getSurchargeReason(), extraPremiumRatio);
    }

    /**
     * 构建创建核保请求（透传险种编码，供核保域 UW-4 按产品查配置）
     */
    private CreateUnderwritingRequest buildCreateRequest(UnderwritingDecisionRequest request) {
        CreateUnderwritingRequest createRequest = new CreateUnderwritingRequest();
        createRequest.setPolicyId(request.insuranceId());
        createRequest.setCustomerId(request.holderId());
        createRequest.setAmount(request.sumInsured());
        createRequest.setCurrency(request.currency());
        createRequest.setUnderwritingType(UnderwritingEnum.UnderwritingType.NEW_BUSINESS);
        createRequest.setRequestDate(LocalDateTime.now());
        createRequest.setRequestBy(resolveOperatorId());
        // 险种编码取投保险种列表首个（承保主险载体）
        createRequest.setProductCode(resolveProductCode(request));
        return createRequest;
    }

    /**
     * 构建结构化核保输入请求（UW-2 富核保核心）
     * <p>
     * 从核保决策请求携带的被保人要素组装职业/体检输入。健康告知等更细粒度信息在 saga 尚不完整时留空，
     * 由核保域评分兜底；至少填充职业类别/BMI 以走富评分路径，不再走金额兜底。
     * </p>
     */
    private SubmitUnderwritingInputApiRequest buildInputRequest(UnderwritingDecisionRequest request) {
        SubmitUnderwritingInputApiRequest inputRequest = new SubmitUnderwritingInputApiRequest();
        inputRequest.setSubmittedBy(resolveOperatorId());

        // 职业信息：有职业类别时填充（意外险/定期寿险风险要素）
        if (request.primaryInsuredOccupationCategory() != null) {
            SubmitUnderwritingInputApiRequest.OccupationInput occupation =
                    new SubmitUnderwritingInputApiRequest.OccupationInput();
            occupation.setOccupationCategory(request.primaryInsuredOccupationCategory());
            inputRequest.setOccupationInfo(occupation);
        }

        // 体检结果：有 BMI 时填充（寿险/重疾健康风险要素）
        if (request.primaryInsuredBmi() != null) {
            SubmitUnderwritingInputApiRequest.PhysicalExamInput exam =
                    new SubmitUnderwritingInputApiRequest.PhysicalExamInput();
            exam.setBmi(request.primaryInsuredBmi());
            inputRequest.setPhysicalExamResult(exam);
        }

        return inputRequest;
    }

    /**
     * 校验核保域同步调用响应，避免空响应在 Saga 内退化为无上下文空指针异常。
     */
    private UnderwritingResponse requireSuccessfulBody(ResponseEntity<UnderwritingResponse> response, String stage,
                                                        UnderwritingDecisionRequest request) {
        if (response == null) {
            throw new BusinessException(stage + "失败: insuranceId=" + request.insuranceId() + ", 无响应");
        }
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new BusinessException(stage + "失败: insuranceId=" + request.insuranceId() + ", HTTP状态="
                    + response.getStatusCode().value());
        }
        if (response.getBody() == null) {
            throw new BusinessException(stage + "响应体为空: insuranceId=" + request.insuranceId());
        }
        return response.getBody();
    }

    /**
     * 提取核保单ID并拒绝无效响应。
     */
    private String requireUnderwritingId(UnderwritingResponse response, String stage,
                                         UnderwritingDecisionRequest request) {
        if (response.getUnderwritingId() == null || response.getUnderwritingId().isBlank()) {
            throw new BusinessException(stage + "核保单ID为空: insuranceId=" + request.insuranceId());
        }
        return response.getUnderwritingId();
    }

    /**
     * 新契约直接采用业务结论；兼容旧版仅返回流程状态的核保响应。
     */
    private ConclusionType resolveConclusion(UnderwritingResponse response, UnderwritingDecisionRequest request) {
        if (response.getConclusionType() != null) {
            return response.getConclusionType();
        }
        if (response.getStatus() != null) {
            return mapToResultCode(response.getStatus());
        }
        throw new BusinessException("核保结论为空: insuranceId=" + request.insuranceId() + ", underwritingId="
                + response.getUnderwritingId());
    }

    /**
     * 构建核保决策请求（出单主链路走自动核保）
     */
    private DecideUnderwritingApiRequest buildDecideRequest(UnderwritingDecisionRequest request) {
        DecideUnderwritingApiRequest decideRequest = new DecideUnderwritingApiRequest();
        decideRequest.setAuditType(AUDIT_TYPE_AUTOMATIC);
        // 核保决策由系统自动作出，主体是系统而非投保人
        decideRequest.setDecidedBy(SYSTEM_OPERATOR);
        // UW-4：透传险种编码，供核保域 application 层查询产品核保配置（加费许可等）
        decideRequest.setProductCode(resolveProductCode(request));
        return decideRequest;
    }

    /**
     * 查询该投保单是否已有核保单，返回其 ID；无则返回 null。
     * <p>幂等键取「投保单号」——同一投保单在重试/重入下必然映射到同一张核保单。</p>
     */
    private String findExistingUnderwritingId(UnderwritingDecisionRequest request) {
        ResponseEntity<List<UnderwritingResponse>> response =
                underwritingApi.getUnderwritingByPolicyId(request.insuranceId());
        if (response == null || response.getBody() == null || response.getBody().isEmpty()) {
            return null;
        }
        return response.getBody().get(0).getUnderwritingId();
    }

    /**
     * 取真实操作人；上下文无操作人时回落系统主体。
     * <p>🔴 绝不回落 {@code holderId}：把投保人记为操作人正是本次修复要消除的审计失真。
     * 用「回落系统主体」而非 {@code requireOperatorId()} 硬失败，是为兼容事件驱动的自动出单
     * （该场景确无人工操作人，业务上应由系统主体承担）。</p>
     */
    private String resolveOperatorId() {
        String operatorId = RequestContextHolder.getOperatorId();
        return operatorId != null && !operatorId.isBlank() ? operatorId : SYSTEM_OPERATOR;
    }

    /**
     * 解析险种编码（取投保险种编码列表首个作为承保主险载体）
     */
    private String resolveProductCode(UnderwritingDecisionRequest request) {
        return request.productCodes() != null && !request.productCodes().isEmpty()
                ? request.productCodes().get(0)
                : null;
    }

    /**
     * 核保域状态 → 保单域核保结论映射（防腐层翻译）
     * <p>
     * 标准/通过承保视为接受(ACCEPT)；加费承保视为修改条件承保(MODIFY)；
     * 拒保/拒绝视为拒绝(REJECT)；其余（待核保/人工审核/延期等）视为延期(POSTPONE)。
     * </p>
     */
    private ConclusionType mapToResultCode(UnderwritingEnum.UnderwritingStatus status) {
        return switch (status) {
            case APPROVED, STANDARD -> ConclusionType.ACCEPT;
            case RATED, EXCLUDED -> ConclusionType.MODIFY;
            case REJECTED, DECLINED -> ConclusionType.REJECT;
            default -> ConclusionType.POSTPONE;
        };
    }
}
