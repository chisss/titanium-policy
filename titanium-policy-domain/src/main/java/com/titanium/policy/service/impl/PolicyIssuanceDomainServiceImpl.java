package com.titanium.policy.service.impl;

import org.springframework.stereotype.Service;

import com.titanium.metadata.enums.CurrencyEnum;
import com.titanium.metadata.enums.underwriting.UnderwritingEnum.ConclusionType;
import com.titanium.metadata.valueobject.Money;
import com.titanium.policy.aggregate.Insurance;
import com.titanium.policy.service.PolicyIssuanceDomainService;
import com.titanium.policy.valueobject.insurance.InsuranceBasicInfo;
import com.titanium.policy.valueobject.insurance.PolicyIssuanceDecision;
import com.titanium.policy.valueobject.insurance.UnderwritingResult;

/**
 * 承保领域服务实现
 * <p>
 * 纯领域计算：仅依赖入参聚合/值对象与领域规则，不注入任何 Port、不发命令、无状态。
 * 承保规则集中于此，应用层不再散落「核保是否通过」「保单要素怎么算」的业务判断。
 * </p>
 */
@Service
public class PolicyIssuanceDomainServiceImpl implements PolicyIssuanceDomainService {

    @Override
    public PolicyIssuanceDecision decideIssuance(Insurance insurance, UnderwritingResult underwritingResult) {
        // 规则一：核保结果缺失，不可承保
        if (underwritingResult == null || underwritingResult.resultCode() == null) {
            return PolicyIssuanceDecision.reject("缺少核保结论，不可承保");
        }

        // 规则二：核保结论映射承保准入——仅 ACCEPT/MODIFY 可承保，REJECT/POSTPONE 不可
        ConclusionType conclusion = underwritingResult.resultCode();
        if (!isAcceptable(conclusion)) {
            String reason = conclusion == ConclusionType.REJECT ? "核保拒绝，不可承保" : "核保暂缓，需人工介入后方可承保";
            return PolicyIssuanceDecision.reject(reason);
        }

        // 规则三：从投保单聚合推导保单构建要素（跨「投保单→保单」的领域转换规则）
        InsuranceBasicInfo basicInfo = insurance.getBasicInfo();
        Money premium = basicInfo != null && basicInfo.exactPremium() != null ? basicInfo.exactPremium()
                : Money.zero(CurrencyEnum.CNY.getCode());

        // 规则四：仅 MODIFY（修改条件承保）携带核保加费/特约条件，ACCEPT 无附加条件
        String underwritingCondition = conclusion == ConclusionType.MODIFY ? underwritingResult.condition() : null;

        return PolicyIssuanceDecision.accept(insurance.getPolicyForm(), basicInfo != null ? basicInfo.holderId() : null,
                premium, basicInfo != null ? basicInfo.insurancePeriodStart() : null,
                basicInfo != null ? basicInfo.insurancePeriodEnd() : null, underwritingCondition);
    }

    @Override
    public boolean canIssueByConclusion(UnderwritingResult underwritingResult) {
        // 复用规则二：核保结论缺失不可承保，否则按准入规则判定
        return underwritingResult != null && underwritingResult.resultCode() != null
                && isAcceptable(underwritingResult.resultCode());
    }

    /**
     * 承保准入规则（单一事实源）：仅核保结论为 ACCEPT（接受）或 MODIFY（修改条件承保）可承保，
     * REJECT（拒绝）/POSTPONE（延期）不可。{@code decideIssuance} 与 {@code canIssueByConclusion} 共用。
     *
     * @param conclusion 核保结论
     * @return 是否准予承保
     */
    private boolean isAcceptable(ConclusionType conclusion) {
        return conclusion == ConclusionType.ACCEPT || conclusion == ConclusionType.MODIFY;
    }
}
