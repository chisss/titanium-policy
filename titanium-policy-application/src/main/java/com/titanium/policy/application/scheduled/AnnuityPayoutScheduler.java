package com.titanium.policy.application.scheduled;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.titanium.policy.application.command.PolicyApplicationService;
import com.titanium.policy.command.PayAnnuityBenefitCommand;
import com.titanium.policy.common.constant.PolicyConstants;
import com.titanium.policy.common.enums.AnnuityPayoutStatus;
import com.titanium.policy.query.repository.AnnuityPayoutPlanViewRepository;
import com.titanium.policy.query.view.AnnuityPayoutPlanView;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 年金逐期给付定时驱动（application/scheduled）
 * <p>
 * 年金给付期内需在每个给付日到达时给付一期生存年金。本调度器按「给付中 + 下一给付日已到」跨租户分页扫描
 * 到期年金计划，逐条派发 {@link PayAnnuityBenefitCommand}，以每行自带 tenantId 定位租户。
 * </p>
 * <p>
 * <b>幂等</b>：给付后聚合推进 {@code nextPayoutDate} 前移、投影同步，到期查询不会再选中同期（依赖读模型
 * 最终一致，日级调度足以收敛）；聚合侧 {@code payNextInstallment} 另守护「非给付中不可推进/不可超期」不变量，
 * 双重兜底。<b>错误隔离</b>：单条给付失败仅告警并继续下一条，不影响批次其余计划。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AnnuityPayoutScheduler {

    /** 单页扫描条数（游标翻页，避免全表加载） */
    private static final int PAGE_SIZE = 200;

    private final AnnuityPayoutPlanViewRepository annuityPayoutPlanViewRepository;

    private final PolicyApplicationService        policyApplicationService;

    /**
     * 定时扫描并给付到期年金（默认每日 01:00，可经配置覆盖）。
     */
    @Scheduled(cron = "${titanium.policy.annuity-payout.cron:0 0 1 * * ?}")
    public void payDueAnnuities() {
        LocalDateTime dueDate = LocalDateTime.now();
        String payingStatus = AnnuityPayoutStatus.PAYING.getCode();
        int page = 0;
        int total = 0;
        int failed = 0;
        while (true) {
            Pageable pageable = PageRequest.of(page, PAGE_SIZE, Sort.by(Sort.Direction.ASC, "id"));
            List<AnnuityPayoutPlanView> due =
                    annuityPayoutPlanViewRepository.findByPayoutStatusAndNextPayoutDateLessThanEqual(payingStatus,
                            dueDate, pageable);
            if (due.isEmpty()) {
                break;
            }
            for (AnnuityPayoutPlanView plan : due) {
                total++;
                try {
                    policyApplicationService.payAnnuityBenefit(new PayAnnuityBenefitCommand(plan.getPolicyId(),
                            PolicyConstants.POLICY_SYSTEM, plan.getTenantId()));
                } catch (Exception e) {
                    failed++;
                    log.warn("[年金给付定时] 单条给付失败, policyId={}, tenantId={}, 原因={}", plan.getPolicyId(),
                            plan.getTenantId(), e.getMessage());
                }
            }
            if (due.size() < PAGE_SIZE) {
                break;
            }
            page++;
        }
        if (total > 0) {
            log.info("[年金给付定时] 本批到期年金给付完成, 命中={}, 失败={}", total, failed);
        }
    }
}
