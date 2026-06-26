package com.titanium.policy.application.service;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;

import com.titanium.metadata.enums.underwriting.UnderwritingEnum;
import com.titanium.metadata.enums.underwriting.UnderwritingEnum.ConclusionType;
import com.titanium.policy.service.UnderwritingDecisionGateway;
import com.titanium.policy.valueobject.insurance.UnderwritingDecisionRequest;
import com.titanium.policy.valueobject.insurance.UnderwritingResult;
import com.titanium.underwriting.api.dto.UnderwritingDTO;
import com.titanium.underwriting.api.request.CreateUnderwritingRequest;
import com.titanium.underwriting.api.request.UnderwriteRequest;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 核保决策网关同步适配器
 * <p>
 * {@link UnderwritingDecisionGateway} 的当前阶段实现：注册中心/消息总线就绪前，
 * 经 {@link UnderwritingService}（Feign）同步调用核保域，完成"创建核保 → 执行核保 → 回传结论"。
 * 将核保域 {@link UnderwritingDTO} 翻译为保单域 {@link UnderwritingResult}，构成防腐层（ACL）。
 * </p>
 * <p>
 * <b>演进说明</b>：后续可新增异步实现（发 {@code SubmitUnderwriting} 命令 + 监听核保域 Kafka 回流事件），
 * 通过切换 Spring Bean 即可替换，投保出单 Saga 编排逻辑无需改动。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SyncUnderwritingDecisionAdapter implements UnderwritingDecisionGateway {

    private final UnderwritingService underwritingService;

    @Override
    public UnderwritingResult requestDecision(UnderwritingDecisionRequest request) {
        log.info("[核保网关] 同步请求核保, insuranceId={}, holderId={}, tenantId={}", request.insuranceId(),
                request.holderId(), request.tenantId());

        // 1. 创建核保单
        UnderwritingDTO created = underwritingService.createUnderwriting(buildCreateRequest(request),
                request.tenantId());
        String underwritingId = created.getUnderwritingId();

        // 2. 执行核保，获取核保结论
        UnderwritingDTO decided = underwritingService.underwrite(underwritingId, buildUnderwriteRequest(request),
                request.tenantId());

        // 3. 核保域结果翻译为保单域核保结果（防腐层）
        ConclusionType resultCode = mapToResultCode(decided.getStatus());
        log.info("[核保网关] 核保完成, insuranceId={}, underwritingId={}, 结论={}", request.insuranceId(),
                underwritingId, resultCode);

        return new UnderwritingResult(underwritingId, resultCode, decided.getReviewComments(),
                decided.getUnderwriterId(), LocalDateTime.now(), decided.getSurchargeReason());
    }

    /**
     * 构建创建核保请求
     */
    private CreateUnderwritingRequest buildCreateRequest(UnderwritingDecisionRequest request) {
        CreateUnderwritingRequest createRequest = new CreateUnderwritingRequest();
        createRequest.setPolicyId(request.insuranceId());
        createRequest.setCustomerId(request.holderId());
        createRequest.setAmount(request.premium());
        createRequest.setCurrency(request.currency());
        createRequest.setUnderwritingType(resolveUnderwritingType(request));
        createRequest.setRequestDate(LocalDateTime.now());
        createRequest.setRequestBy(request.holderId());
        return createRequest;
    }

    /**
     * 构建执行核保请求
     */
    private UnderwriteRequest buildUnderwriteRequest(UnderwritingDecisionRequest request) {
        UnderwriteRequest underwriteRequest = new UnderwriteRequest();
        underwriteRequest.setAmount(request.premium());
        underwriteRequest.setUnderwriteDate(LocalDateTime.now());
        underwriteRequest.setReason("投保出单 Saga 自动提交核保");
        return underwriteRequest;
    }

    /**
     * 解析核保类型（投保出单 Saga 触发的核保为新单核保）
     */
    private UnderwritingEnum.UnderwritingType resolveUnderwritingType(UnderwritingDecisionRequest request) {
        return UnderwritingEnum.UnderwritingType.NEW_BUSINESS;
    }

    /**
     * 核保域状态 → 保单域核保结论映射（防腐层翻译）
     * <p>
     * 标准/通过承保视为接受(ACCEPT)；加费承保视为修改条件承保(MODIFY)；
     * 拒保/拒绝视为拒绝(REJECT)；其余（待核保/人工审核/延期等）视为延期(POSTPONE)。
     * </p>
     */
    private ConclusionType mapToResultCode(UnderwritingEnum.UnderwritingStatus status) {
        if (status == null) {
            return ConclusionType.POSTPONE;
        }
        return switch (status) {
            case APPROVED, STANDARD -> ConclusionType.ACCEPT;
            case RATED, EXCLUDED -> ConclusionType.MODIFY;
            case REJECTED, DECLINED -> ConclusionType.REJECT;
            default -> ConclusionType.POSTPONE;
        };
    }
}
