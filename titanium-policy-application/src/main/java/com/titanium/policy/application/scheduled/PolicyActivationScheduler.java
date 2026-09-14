package com.titanium.policy.application.scheduled;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.titanium.metadata.enums.policy.PolicyEnum;
import com.titanium.policy.application.command.policy.PolicyApplicationService;
import com.titanium.policy.query.repository.PolicyViewRepository;
import com.titanium.policy.query.view.PolicyView;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 待生效保单定时激活补偿（application/scheduled）
 * <p>
 * 出单后保单停在待生效态，收费条件满足时由支付回调即时驱动生效
 * （{@code PremiumCollectionResultOrchestrator}）。但激活另有一重前置校验——<b>保障起期已到</b>，
 * 而「今日投保、次日起保」是标准场景：回调到达时起期尚未到达，即时激活必然被聚合拒绝，
 * 彼时仅有日志留痕。本调度器即该场景的补偿通道：按「待生效 + 起期已到」跨租户分页扫描，
 * 逐条派发 {@link com.titanium.policy.command.ActivatePolicyCommand}，以每行自带 tenantId 定位租户。
 * </p>
 * <p>
 * 🔴 <b>不复制业务规则</b>：候选保单能否真正生效，全部交由聚合 {@code canActivate()} 判定——
 * 保费未收讫、已生效、已终止等一律由其拒绝，本调度器只负责「起期到了就来试一次」这一触发职责。
 * 故本类无任何收费或状态判断分支，与满期/年金调度器同构。
 * </p>
 * <p>
 * <b>幂等</b>：激活成功后保单转生效、投影同步，下轮扫描不再选中（依赖读模型最终一致，日级调度足以收敛）；
 * 聚合侧前置校验另作兜底。<b>错误隔离</b>：单条失败仅告警并继续，不影响批次其余保单。
 * </p>
 * <p>
 * ⚠️ <b>持续未收讫的保单会每轮重复命中并告警</b>（起期已到但保费始终未到账）——这是有意为之：
 * 该告警即「保单卡在待生效」的人工介入信号，失效应由 billing 逾期失效链路承担，本类不越权处置。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PolicyActivationScheduler {

    /** 单页扫描条数（游标翻页，避免全表加载） */
    private static final int PAGE_SIZE = 200;

    private final PolicyViewRepository     policyViewRepository;

    private final PolicyApplicationService policyApplicationService;

    /**
     * 定时扫描并激活起期已到的待生效保单（默认每日 02:00，可经配置覆盖）。
     */
    @Scheduled(cron = "${titanium.policy.activation.cron:0 0 2 * * ?}")
    public void activateDuePolicies() {
        LocalDateTime dueDate = LocalDateTime.now();
        int page = 0;
        int total = 0;
        int failed = 0;
        while (true) {
            Pageable pageable = PageRequest.of(page, PAGE_SIZE, Sort.by(Sort.Direction.ASC, "policyId"));
            List<PolicyView> due = policyViewRepository.findByPolicyStatusAndStartDateLessThanEqual(
                    PolicyEnum.PolicyStatus.PENDING_EFFECTIVE, dueDate, pageable);
            if (due.isEmpty()) {
                break;
            }
            for (PolicyView policy : due) {
                total++;
                try {
                    policyApplicationService.activatePolicy(policy.getPolicyId(), policy.getTenantId());
                } catch (Exception e) {
                    failed++;
                    log.warn("[待生效激活定时] 单条激活失败（保费未收讫属预期，需人工跟进）, policyId={}, policyNo={}, "
                            + "tenantId={}, 原因={}", policy.getPolicyId(), policy.getPolicyNo(), policy.getTenantId(),
                            e.getMessage());
                }
            }
            if (due.size() < PAGE_SIZE) {
                break;
            }
            page++;
        }
        if (total > 0) {
            log.info("[待生效激活定时] 本批起期已到保单激活完成, 命中={}, 成功={}, 失败={}", total, total - failed, failed);
        }
    }
}
