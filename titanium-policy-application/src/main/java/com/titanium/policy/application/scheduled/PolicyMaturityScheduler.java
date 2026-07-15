package com.titanium.policy.application.scheduled;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.titanium.metadata.enums.insurance.InsuranceProductType;
import com.titanium.metadata.enums.policy.PolicyEnum;
import com.titanium.policy.application.command.PolicyApplicationService;
import com.titanium.policy.command.MatureDuePolicyCommand;
import com.titanium.policy.common.constant.PolicyConstants;
import com.titanium.policy.query.repository.PolicyViewRepository;
import com.titanium.policy.query.view.PolicyView;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 保单满期给付定时驱动（application/scheduled）
 * <p>
 * 两全险（生存给付型）保单在保险期间届满、被保险人生存时给付满期金并转满期（EXPIRED）。本调度器按
 * 「生效 + 两全险 + 止期已到」跨租户分页扫描到期保单，逐条派发 {@link MatureDuePolicyCommand}，
 * 满期金额由聚合自身基本保额推导，以每行自带 tenantId 定位租户。
 * </p>
 * <p>
 * <b>幂等</b>：给付后保单转 EXPIRED、投影同步，到期查询不再选中（依赖读模型最终一致，日级调度足以收敛）；
 * 聚合侧另守护「仅生效两全险可满期」不变量兜底。<b>错误隔离</b>：单条失败仅告警并继续，不影响批次其余保单。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PolicyMaturityScheduler {

    /** 单页扫描条数（游标翻页，避免全表加载） */
    private static final int PAGE_SIZE = 200;

    private final PolicyViewRepository      policyViewRepository;

    private final PolicyApplicationService  policyApplicationService;

    /**
     * 定时扫描并满期给付到期保单（默认每日 01:30，可经配置覆盖）。
     */
    @Scheduled(cron = "${titanium.policy.maturity.cron:0 30 1 * * ?}")
    public void matureDuePolicies() {
        LocalDateTime dueDate = LocalDateTime.now();
        int page = 0;
        int total = 0;
        int failed = 0;
        while (true) {
            Pageable pageable = PageRequest.of(page, PAGE_SIZE, Sort.by(Sort.Direction.ASC, "policyId"));
            List<PolicyView> due = policyViewRepository.findByPolicyStatusAndInsuranceTypeAndEndDateLessThanEqual(
                    PolicyEnum.PolicyStatus.EFFECTIVE, InsuranceProductType.ENDOWMENT, dueDate, pageable);
            if (due.isEmpty()) {
                break;
            }
            for (PolicyView policy : due) {
                total++;
                try {
                    policyApplicationService.matureDuePolicy(new MatureDuePolicyCommand(policy.getPolicyId(),
                            PolicyConstants.POLICY_SYSTEM, policy.getTenantId()));
                } catch (Exception e) {
                    failed++;
                    log.warn("[满期给付定时] 单条满期给付失败, policyId={}, tenantId={}, 原因={}", policy.getPolicyId(),
                            policy.getTenantId(), e.getMessage());
                }
            }
            if (due.size() < PAGE_SIZE) {
                break;
            }
            page++;
        }
        if (total > 0) {
            log.info("[满期给付定时] 本批到期满期给付完成, 命中={}, 失败={}", total, failed);
        }
    }
}
