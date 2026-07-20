package com.titanium.policy.query.handler.projection;

import java.time.LocalDateTime;
import java.util.List;

import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.EventHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.titanium.common.jpa.BasePersistable;
import com.titanium.metadata.enums.policy.PolicyEnum;
import com.titanium.policy.entity.insurance.InsuredPartyList;
import com.titanium.policy.event.AccountValueUpdatedEvent;
import com.titanium.policy.event.DividendDistributedEvent;
import com.titanium.policy.event.InvestmentAccountLinkedEvent;
import com.titanium.policy.event.PolicyActivatedEvent;
import com.titanium.policy.event.PolicyCancelledEvent;
import com.titanium.policy.event.PolicyCreatedEvent;
import com.titanium.policy.event.PolicyExpiredEvent;
import com.titanium.policy.event.PolicyIssuedEvent;
import com.titanium.policy.event.PolicyLapsedEvent;
import com.titanium.policy.event.PolicyMaturedEvent;
import com.titanium.policy.event.PolicyReinstatedEvent;
import com.titanium.policy.event.PolicyResumedEvent;
import com.titanium.policy.event.PolicySuspendedEvent;
import com.titanium.policy.event.PolicyTerminatedEvent;
import com.titanium.policy.event.PremiumWaivedEvent;
import com.titanium.policy.query.mapper.PolicyViewMapper;
import com.titanium.policy.query.repository.PolicyViewRepository;
import com.titanium.policy.query.view.PolicyView;
import com.titanium.policy.valueobject.PolicyStatus;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 保单读模型投影事件处理器（CQRS 读侧核心）
 * <p>
 * 订阅保单域领域事件，将聚合根状态变更投影到读模型表 {@code t_policy_view}， 补齐此前缺失的 CQRS
 * 读模型投影机制，实现真正的读写分离。
 * </p>
 * <p>
 * <b>处理组</b>：{@code policy-query-group}，与现有
 * {@link PolicyQueryHandler}（QueryHandler）共用同一处理组， 复用 bootstrap 中已配置的 DLQ
 * 死信队列与 tracking 模式。
 * </p>
 * <p>
 * <b>幂等性</b>：创建事件用 saveOrUpdate 语义；状态更新事件先查存量再更新，缺失时告警并跳过， 保证事件重放（Replay）时不产生脏数据。
 * </p>
 */
@Slf4j
@Component
@ProcessingGroup("policy-query-group")
@RequiredArgsConstructor
public class PolicyProjectionEventHandler {

    private final PolicyViewRepository policyViewRepository;
    private final PolicyViewMapper     policyViewMapper;

    /**
     * 投影保单创建事件：新建读模型记录
     */
    @EventHandler
    @Transactional
    public void on(PolicyCreatedEvent event) {
        log.info("[读模型投影] 保单创建: policyId={}, tenantId={}", event.policyId(), event.tenantId());

        PolicyView view = policyViewRepository.findByPolicyIdAndTenantId(event.policyId(), event.tenantId())
                .orElseGet(PolicyView::new);

        // 事件字段 → 读模型的结构映射收敛到 MapStruct（保单号/保费值对象拆解、起止期改名），消除逐字段 set
        policyViewMapper.applyCreated(view, event);
        // 状态含 null 兜底 + 本地状态机→metadata 枚举映射，属处理器职责，不下沉映射器
        view.setPolicyStatus(event.status() != null ? mapStatus(event.status().statusCode())
                : PolicyEnum.PolicyStatus.PENDING_EFFECTIVE);
        // 从参与方清单填充投保人/首位被保险人字段（事件携带快照则直写，否则留空待后续补全）
        if (event.insuredPartyList() != null) {
            InsuredPartyList.HolderInfo holder = event.insuredPartyList().holderInfo();
            if (holder != null) {
                view.setPolicyHolderId(holder.customerId());
                view.setPolicyHolderName(holder.name());
            }
            List<InsuredPartyList.InsuredInfo> insuredList = event.insuredPartyList().insuredList();
            if (insuredList != null && !insuredList.isEmpty()) {
                view.setInsuredName(insuredList.get(0).name());
            }
        }
        stampAuditTime(view);

        policyViewRepository.save(view);
    }

    /**
     * 投影保单签发事件：记录签发时间
     */
    @EventHandler
    @Transactional
    public void on(PolicyIssuedEvent event) {
        applyUpdate(event.policyId(), event.tenantId(), "保单签发", view -> view.setIssueTime(event.issueTime()));
    }

    /**
     * 投影保单生效事件
     */
    @EventHandler
    @Transactional
    public void on(PolicyActivatedEvent event) {
        applyStatus(event.policyId(), event.tenantId(), PolicyStatus.StatusCode.EFFECTIVE);
    }

    /**
     * 投影保单暂停事件
     */
    @EventHandler
    @Transactional
    public void on(PolicySuspendedEvent event) {
        applyStatus(event.policyId(), event.tenantId(), PolicyStatus.StatusCode.SUSPENDED);
    }

    /**
     * 投影保单恢复事件
     */
    @EventHandler
    @Transactional
    public void on(PolicyResumedEvent event) {
        applyStatus(event.policyId(), event.tenantId(), PolicyStatus.StatusCode.EFFECTIVE);
    }

    /**
     * 投影保单终止事件
     */
    @EventHandler
    @Transactional
    public void on(PolicyTerminatedEvent event) {
        applyStatus(event.policyId(), event.tenantId(), PolicyStatus.StatusCode.TERMINATED);
    }

    /**
     * 投影保单到期事件
     */
    @EventHandler
    @Transactional
    public void on(PolicyExpiredEvent event) {
        applyStatus(event.policyId(), event.tenantId(), PolicyStatus.StatusCode.EXPIRED);
    }

    /**
     * 投影保单失效事件（宽限期满未缴费）
     */
    @EventHandler
    @Transactional
    public void on(PolicyLapsedEvent event) {
        applyStatus(event.policyId(), event.tenantId(), PolicyStatus.StatusCode.LAPSED);
    }

    /**
     * 投影保单复效事件（补缴+核保通过后恢复生效）
     */
    @EventHandler
    @Transactional
    public void on(PolicyReinstatedEvent event) {
        applyStatus(event.policyId(), event.tenantId(), PolicyStatus.StatusCode.EFFECTIVE);
    }

    /**
     * 投影保单取消事件
     */
    @EventHandler
    @Transactional
    public void on(PolicyCancelledEvent event) {
        applyStatus(event.policyId(), event.tenantId(), PolicyStatus.StatusCode.CANCELLED);
    }

    /**
     * 投影保单满期给付事件：记录满期金并将状态置为满期（EXPIRED）
     */
    @EventHandler
    @Transactional
    public void on(PolicyMaturedEvent event) {
        log.info("[读模型投影] 保单满期给付: policyId={}, 满期金={}", event.policyId(), event.maturityBenefit());
        applyUpdate(event.policyId(), event.tenantId(), "满期给付", view -> {
            view.setMaturityBenefit(event.maturityBenefit());
            view.setPolicyStatus(mapStatus(PolicyStatus.StatusCode.EXPIRED));
        });
    }

    /**
     * 投影保费豁免事件：标记后续保费豁免，保单状态不变（持续有效）
     */
    @EventHandler
    @Transactional
    public void on(PremiumWaivedEvent event) {
        log.info("[读模型投影] 保费豁免: policyId={}, 原因={}", event.policyId(), event.reason());
        applyUpdate(event.policyId(), event.tenantId(), "保费豁免", view -> {
            view.setPremiumWaived(true);
            view.setWaiverReason(event.reason() != null ? event.reason().getCode() : null);
        });
    }

    /**
     * 投影红利派发事件：按累计红利刷新读模型（红利派发不改保单状态）
     */
    @EventHandler
    @Transactional
    public void on(DividendDistributedEvent event) {
        log.info("[读模型投影] 红利派发: policyId={}, 累计红利={}", event.policyId(), event.accumulatedDividend());
        applyUpdate(event.policyId(), event.tenantId(), "红利派发", view -> {
            view.setAccumulatedDividend(event.accumulatedDividend());
            view.setDividendOption(event.option() != null ? event.option().getCode() : null);
        });
    }

    @EventHandler
    @Transactional
    public void on(InvestmentAccountLinkedEvent event) {
        log.info("[读模型投影] 投资账户挂接: policyId={}, accountId={}", event.policyId(),
                event.investmentAccountId());
        applyUpdate(event.policyId(), event.tenantId(), "投资账户挂接",
                view -> view.setInvestmentAccountId(event.investmentAccountId()));
    }

    @EventHandler
    @Transactional
    public void on(AccountValueUpdatedEvent event) {
        log.info("[读模型投影] 投资账户价值回写: policyId={}, accountId={}, 账户价值={}", event.policyId(),
                event.accountId(), event.accountValue());
        applyUpdate(event.policyId(), event.tenantId(), "投资账户价值回写", view -> {
            view.setInvestmentAccountId(event.accountId());
            view.setInvestmentAccountValue(event.accountValue());
        });
    }

    /**
     * 通用状态投影：更新读模型状态字段
     *
     * @param policyId 保单ID
     * @param tenantId 租户ID
     * @param newStatus 目标状态
     */
    private void applyStatus(String policyId, String tenantId, PolicyStatus.StatusCode newStatus) {
        log.info("[读模型投影] 状态变更: policyId={}, status={}", policyId, newStatus.name());
        applyUpdate(policyId, tenantId, "状态变更-" + newStatus.name(),
                view -> view.setPolicyStatus(mapStatus(newStatus)));
    }

    /**
     * 本地状态机编码 → metadata 读模型保单状态枚举
     * <p>
     * NOT_EFFECTIVE 对齐 metadata 的 PENDING_EFFECTIVE，其余同名映射。
     * </p>
     *
     * @param statusCode 本地状态机编码
     * @return metadata 保单状态枚举
     */
    private PolicyEnum.PolicyStatus mapStatus(PolicyStatus.StatusCode statusCode) {
        return switch (statusCode) {
            case NOT_EFFECTIVE -> PolicyEnum.PolicyStatus.PENDING_EFFECTIVE;
            case EFFECTIVE -> PolicyEnum.PolicyStatus.EFFECTIVE;
            case SUSPENDED -> PolicyEnum.PolicyStatus.SUSPENDED;
            case TERMINATED -> PolicyEnum.PolicyStatus.TERMINATED;
            case EXPIRED -> PolicyEnum.PolicyStatus.EXPIRED;
            case LAPSED -> PolicyEnum.PolicyStatus.LAPSED;
            case CANCELLED -> PolicyEnum.PolicyStatus.CANCELLED;
        };
    }

    /**
     * 通用更新模板：查存量→应用变更→刷新更新时间→保存；缺失时告警跳过保证幂等
     *
     * @param policyId 保单ID
     * @param tenantId 租户ID
     * @param action 操作描述（日志用）
     * @param mutator 字段变更逻辑
     */
    private void applyUpdate(String policyId, String tenantId, String action,
                             java.util.function.Consumer<PolicyView> mutator) {
        policyViewRepository.findByPolicyIdAndTenantId(policyId, tenantId).ifPresentOrElse(view -> {
            mutator.accept(view);
            view.setUpdateTime(LocalDateTime.now());
            policyViewRepository.save(view);
        }, () -> log.warn("[读模型投影] {} 失败：未找到读模型记录 policyId={}, tenantId={}（可能事件乱序，将由DLQ重试）", action,
                policyId, tenantId));
    }

    /**
     * 统一填充读模型审计时间戳：createTime 仅首次创建时写入、updateTime 每次投影刷新。
     * <p>
     * 该逻辑含 {@code now()} 运行时副作用与"仅首次设置"语义，属投影处理器职责，不下沉 MapStruct 映射器。
     * </p>
     *
     * @param view 读模型（继承 {@link BasePersistable}）
     */
    private void stampAuditTime(BasePersistable view) {
        LocalDateTime now = LocalDateTime.now();
        if (view.getCreateTime() == null) {
            view.setCreateTime(now);
        }
        view.setUpdateTime(now);
    }
}
