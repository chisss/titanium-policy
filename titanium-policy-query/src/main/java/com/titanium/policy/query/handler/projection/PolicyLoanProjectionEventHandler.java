package com.titanium.policy.query.handler.projection;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.EventHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.titanium.common.jpa.BasePersistable;
import com.titanium.policy.event.PolicyLoanDisbursedEvent;
import com.titanium.policy.event.PolicyLoanRepaidEvent;
import com.titanium.policy.query.mapper.PolicyViewMapper;
import com.titanium.policy.query.repository.PolicyLoanViewRepository;
import com.titanium.policy.query.view.PolicyLoanView;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 保单贷款明细读模型投影处理器（CQRS 读侧，g04-03）。
 * <p>
 * 订阅贷款放款事件，投影到读模型表 {@code t_policy_loan}：每笔放款一行，主键 = 贷款明细业务号
 * {@code loanId}（幂等 upsert）。纯读模型投影，<b>不发命令、不做跨聚合编排</b>。
 * </p>
 */
@Slf4j
@Component
@ProcessingGroup("policy-query-group")
@RequiredArgsConstructor
public class PolicyLoanProjectionEventHandler {

    private final PolicyLoanViewRepository loanViewRepository;
    private final PolicyViewMapper          policyViewMapper;

    /**
     * 投影放款事件：按贷款明细业务号幂等 upsert 一行未偿明细
     */
    @EventHandler
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(PolicyLoanDisbursedEvent event) {
        log.info("[贷款投影] 放款: policyId={}, loanId={}, amount={}", event.policyId(), event.loanId(),
                event.loanAmount());
        PolicyLoanView view = loanViewRepository.findById(event.loanId())
                .orElseGet(() -> newLoanView(event));

        // 事件字段 → 读模型的结构映射收敛到 MapStruct（主键取 loanId），消除逐字段 set
        policyViewMapper.applyLoanDisbursed(view, event);
        stampAuditTime(view);

        loanViewRepository.save(view);
    }

    /**
     * 投影还款事件：就地刷新未偿余额与结清标记（g04-04）。
     * <p>
     * 幂等：按还款后快照整值覆盖，重放同一事件结果收敛（不累加）。
     * </p>
     */
    @EventHandler
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(PolicyLoanRepaidEvent event) {
        log.info("[贷款投影] 还款: policyId={}, loanId={}, repaymentId={}, amount={}, settled={}",
                event.policyId(), event.loanId(), event.repaymentId(), event.repaymentAmount(), event.settled());
        PolicyLoanView view = loanViewRepository.findById(event.loanId())
                .orElseThrow(() -> new IllegalStateException(
                        "贷款明细读模型缺失，还款事件无对应放款行: " + event.loanId()));

        policyViewMapper.applyLoanRepaid(view, event);
        stampAuditTime(view);

        loanViewRepository.save(view);
    }

    /**
     * 新建贷款明细读模型行（仅「首次投影放款事件」时执行），承载创建期初值。
     * <p>
     * 🔴 这些初值<b>不放进 MapStruct 映射器</b>——映射器每次投影放款事件都会执行，DLQ 重试重放时
     * 会把已还款行的余额与结清标记<b>重置</b>回初始值。初值只属于「新建行」这一刻的语义。
     * </p>
     */
    private PolicyLoanView newLoanView(PolicyLoanDisbursedEvent event) {
        PolicyLoanView view = new PolicyLoanView();
        view.setId(event.loanId());
        view.setRepaid(0);
        view.setOutstandingPrincipal(event.loanAmount());
        view.setUnpaidInterest(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        return view;
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
