package com.titanium.policy.application.query;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.titanium.policy.port.product.PolicyCashValuePort;
import com.titanium.policy.query.repository.PolicyViewRepository;
import com.titanium.policy.query.view.PolicyView;
import com.titanium.policy.valueobject.product.PolicyCashValue;

import lombok.RequiredArgsConstructor;

/**
 * 保单现金价值查询应用服务（application/query 读入口）。
 * <p>
 * 职责是「保单号 → 保单要素 → 现金价值」的翻译编排：从读模型取保单要素（产品、总保费、生效日），
 * 推导保单年度后委托 {@link PolicyCashValuePort} 向产品域取数。计算规则本身属产品域，本服务不含
 * 任何费率逻辑。
 * </p>
 * <p>
 * 读模型的选择：本查询服务于**业务决策**（垫缴判定），但取的是保单自身的静态要素（产品/保费/生效日），
 * 出单后不再变更，读模型的最终一致延迟（毫秒级）对日级扫描无影响；而现金价值本身必走产品域实时
 * 查询，不读保单侧任何缓存副本，故不构成「读模型决策漂移」。
 * </p>
 */
@Service
@RequiredArgsConstructor
public class PolicyCashValueQueryAppService {

    private final PolicyViewRepository policyViewRepository;
    private final PolicyCashValuePort policyCashValuePort;

    /**
     * 查指定保单在估值日的现金价值。
     *
     * @param tenantId 租户ID
     * @param policyId 保单ID
     * @param valuationDate 估值日
     * @return 现金价值；保单不存在、关键要素缺失或产品域无适用策略时返回空
     */
    public Optional<PolicyCashValue> query(String tenantId, String policyId, LocalDate valuationDate) {
        LocalDate at = valuationDate != null ? valuationDate : LocalDate.now();
        return policyViewRepository.findByPolicyIdAndTenantId(policyId, tenantId)
                .filter(this::hasCashValueBasis)
                .flatMap(view -> policyCashValuePort.getCashValue(tenantId, view.getProductId(),
                        policyYearOf(view.getStartDate(), at), view.getTotalPremium(),
                        view.getStartDate().toLocalDate(), at));
    }

    /** 现金价值计算的三项必要保单要素：产品（定位策略）、总保费（基数）、生效日（年度与犹豫期基准）。 */
    private boolean hasCashValueBasis(PolicyView view) {
        return view.getProductId() != null && view.getTotalPremium() != null && view.getStartDate() != null;
    }

    /**
     * 保单年度：生效日起算，满一年进下一保单年度，故年差 + 1。
     * <p>
     * 估值日早于生效日属异常数据（退保价值策略无第 0 年度配置），下界收敛到 1 以免查出空策略。
     * </p>
     */
    private int policyYearOf(LocalDateTime startDate, LocalDate valuationDate) {
        int elapsedYears = Period.between(startDate.toLocalDate(), valuationDate).getYears();
        return Math.max(1, elapsedYears + 1);
    }
}
