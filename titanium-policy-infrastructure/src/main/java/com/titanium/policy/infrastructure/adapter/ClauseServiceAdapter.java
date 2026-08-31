package com.titanium.policy.infrastructure.adapter;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.stereotype.Component;

import com.titanium.clause.api.ClauseApi;
import com.titanium.clause.api.response.ClauseResponse;
import com.titanium.clause.api.response.CoverageResponse;
import com.titanium.metadata.enums.CurrencyEnum;
import com.titanium.metadata.enums.clause.DeductibleType;
import com.titanium.metadata.valueobject.Money;
import com.titanium.policy.port.ClauseServicePort;
import com.titanium.policy.valueobject.policy.ClauseSnapshot;
import com.titanium.policy.valueobject.policy.CoverageSnapshot;

import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 条款服务适配器
 * <p>
 * {@link ClauseServicePort} 的基础设施实现，调用条款域 {@link ClauseApi}（Feign）并把条款域 Response
 * 翻译为保单域的快照值对象（防腐）。出单时取到的条款与责任在此转为不可变快照，签发即冻结。
 * </p>
 * <p>
 * 责任快照的<b>挂载信息</b>（{@code attachLevel}/{@code attachRefId}）在此留空——条款域只定义
 * 「有哪些责任」，不知道本次投保有几个标的；挂载由出单装配器依标的结构决定后填充。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClauseServiceAdapter implements ClauseServicePort {

    /** 缺省币种（条款域责任保额未带币种，按人民币处理） */
    private static final String DEFAULT_CURRENCY = CurrencyEnum.CNY.getCode();

    private final ClauseApi clauseApi;

    @Override
    public ClauseSnapshot fetchClauseSnapshot(String clauseId, boolean isMainClause, String tenantId) {
        try {
            ClauseResponse response = clauseApi.getClauseById(clauseId, tenantId);
            if (response == null) {
                log.warn("条款不存在，无法装配条款快照: clauseId={}, tenantId={}", clauseId, tenantId);
                return null;
            }
            return new ClauseSnapshot(response.getClauseId(), response.getClauseCode(), response.getClauseName(),
                    response.getVersion(), isMainClause);
        } catch (FeignException e) {
            log.warn("条款服务调用失败（{}），跳过条款装配: clauseId={}, error={}",
                    e.status(), clauseId, e.getMessage());
            return null;
        }
    }

    @Override
    public List<CoverageSnapshot> fetchCoverageSnapshots(String clauseId, String tenantId) {
        try {
            List<CoverageResponse> coverages = clauseApi.getCoveragesByClauseId(clauseId, tenantId);
            if (coverages == null || coverages.isEmpty()) {
                log.warn("条款下无保险责任，保单将无责任明细: clauseId={}, tenantId={}", clauseId, tenantId);
                return List.of();
            }
            log.info("取得条款责任清单: clauseId={}, 责任数={}", clauseId, coverages.size());
            return coverages.stream().map(this::toSnapshot).toList();
        } catch (FeignException e) {
            log.warn("条款责任服务调用失败（{}），保单将无责任明细: clauseId={}, error={}",
                    e.status(), clauseId, e.getMessage());
            return List.of();
        }
    }

    /**
     * 条款域责任 Response → 保单域责任快照（挂载信息留空，待装配器填充）。
     * <p>
     * 责任保额优先取结构化赔付规则的赔付上限（{@code maxPayout}，如「一般医疗年度 400 万」），
     * 缺省时回退责任本身的保额字段。
     * </p>
     */
    private CoverageSnapshot toSnapshot(CoverageResponse response) {
        BigDecimal sumInsuredValue = response.getMaxPayout() != null
                ? response.getMaxPayout()
                : response.getCoverageAmount();
        return new CoverageSnapshot(response.getCoverageId(), response.getCoverageCode(), response.getCoverageName(),
                response.getCoverageType(), null, null,
                sumInsuredValue != null ? Money.of(sumInsuredValue, DEFAULT_CURRENCY) : null,
                response.getReimbursementRatio(), parseDeductibleType(response.getDeductibleType()),
                response.getDeductibleAmount(), response.getDeductibleRatio(),
                response.getWaitingPeriodDays() != null ? response.getWaitingPeriodDays() : 0, null);
    }

    /**
     * 免赔类型码 → 枚举（空安全，非法值按无免赔处理）。
     */
    private DeductibleType parseDeductibleType(String code) {
        if (code == null || code.isBlank()) {
            return DeductibleType.NONE;
        }
        DeductibleType type = DeductibleType.fromCode(code);
        return type != null ? type : DeductibleType.NONE;
    }
}
