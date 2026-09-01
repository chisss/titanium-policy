package com.titanium.policy.infrastructure.adapter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import com.titanium.common.exception.BusinessException;
import com.titanium.metadata.enums.underwriting.UnderwritingEnum;
import com.titanium.metadata.enums.underwriting.UnderwritingEnum.ConclusionType;
import com.titanium.policy.port.UnderwritingDecisionGateway;
import com.titanium.policy.valueobject.insurance.UnderwritingDecisionRequest;
import com.titanium.policy.valueobject.insurance.UnderwritingResult;
import com.titanium.underwriting.api.UnderwritingApi;
import com.titanium.underwriting.api.request.CreateUnderwritingRequest;
import com.titanium.underwriting.api.request.DecideUnderwritingApiRequest;
import com.titanium.underwriting.api.request.SubmitUnderwritingInputApiRequest;
import com.titanium.underwriting.api.response.UnderwritingResponse;

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

    private final UnderwritingApi underwritingApi;

    @Override
    public UnderwritingResult requestDecision(UnderwritingDecisionRequest request) {
        log.info("[核保网关] 同步请求富核保, insuranceId={}, holderId={}, tenantId={}", request.insuranceId(),
                request.holderId(), request.tenantId());

        // 1. 创建核保单（透传险种编码供核保域按产品配置决策——UW-4）
        ResponseEntity<UnderwritingResponse> createdResponse = underwritingApi.createUnderwriting(
                buildCreateRequest(request), request.tenantId());
        UnderwritingResponse created = requireSuccessfulBody(createdResponse, "创建核保", request);
        String underwritingId = requireUnderwritingId(created, "创建核保", request);

        // 2. 提交结构化核保输入（被保人风险要素 → 富核保评分依据），替代旧金额兜底
        ResponseEntity<UnderwritingResponse> submittedResponse = underwritingApi.submitInput(underwritingId,
                buildInputRequest(request), request.tenantId());
        requireSuccessfulBody(submittedResponse, "提交核保输入", request);

        // 3. 触发核保决策（核保域基于已提交输入产出富结论/风险等级/加费）
        ResponseEntity<UnderwritingResponse> decidedResponse = underwritingApi.decide(underwritingId,
                buildDecideRequest(request), request.tenantId());
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
        createRequest.setRequestBy(request.holderId());
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
        inputRequest.setSubmittedBy(request.holderId());

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
        decideRequest.setDecidedBy(request.holderId());
        // UW-4：透传险种编码，供核保域 application 层查询产品核保配置（加费许可等）
        decideRequest.setProductCode(resolveProductCode(request));
        return decideRequest;
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
