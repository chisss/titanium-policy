package com.titanium.policy.query.handler.projection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.titanium.policy.event.PolicyLoanDisbursedEvent;
import com.titanium.policy.event.PolicyLoanRepaidEvent;
import com.titanium.policy.query.mapper.PolicyViewMapperImpl;
import com.titanium.policy.query.repository.PolicyLoanViewRepository;
import com.titanium.policy.query.view.PolicyLoanView;

/**
 * 保单贷款明细投影处理器测试（g04-03 放款建行 / g04-04 还款更新）。
 * <p>
 * 锁定「放款事件 → 读模型一行」与「还款事件 → 余额与结清标记就地刷新」：主键取贷款明细业务号、
 * 字段逐一映射、创建期初值只在新建行时写入、审计时间戳由处理器填充、已存在读模型时幂等更新。
 * </p>
 */
class PolicyLoanProjectionEventHandlerTest {

    private static final String LOAN_ID  = "LOAN-0001";
    private static final String POLICY_ID = "POL-0001";
    private static final String TENANT_ID = "T1";
    private static final LocalDateTime LOAN_DATE = LocalDateTime.of(2026, 9, 28, 10, 0);
    private static final LocalDateTime REPAY_DATE = LocalDateTime.of(2026, 12, 26, 10, 0);

    private PolicyLoanViewRepository       viewRepository;
    private PolicyLoanProjectionEventHandler handler;

    @BeforeEach
    void setUp() {
        viewRepository = mock(PolicyLoanViewRepository.class);
        handler = new PolicyLoanProjectionEventHandler(viewRepository, new PolicyViewMapperImpl());
    }

    @Test
    void shouldUpsertNewLoanRowOnDisburse() {
        when(viewRepository.findById(LOAN_ID)).thenReturn(Optional.empty());

        handler.on(disbursedEvent());

        ArgumentCaptor<PolicyLoanView> captor = ArgumentCaptor.forClass(PolicyLoanView.class);
        verify(viewRepository).save(captor.capture());
        PolicyLoanView saved = captor.getValue();
        assertEquals(LOAN_ID, saved.getId());
        assertEquals(POLICY_ID, saved.getPolicyId());
        assertEquals(0, new BigDecimal("50000.00").compareTo(saved.getLoanAmount()));
        assertEquals(0, new BigDecimal("0.05").compareTo(saved.getInterestRate()));
        assertEquals(LOAN_DATE, saved.getLoanDate());
        assertEquals(0, saved.getRepaid());
        // 创建期初值：未偿本金 = 放款本金、已计未付利息 = 0
        assertEquals(0, new BigDecimal("50000.00").compareTo(saved.getOutstandingPrincipal()));
        assertEquals(0, BigDecimal.ZERO.compareTo(saved.getUnpaidInterest()));
        assertEquals(TENANT_ID, saved.getTenantId());
        assertNotNull(saved.getCreateTime());
        assertNotNull(saved.getUpdateTime());
    }

    @Test
    void shouldUpdateBalanceAndSettledFlagOnRepay() {
        PolicyLoanView existing = newLoanRow(new BigDecimal("50000.00"), BigDecimal.ZERO, 0);
        when(viewRepository.findById(LOAN_ID)).thenReturn(Optional.of(existing));

        handler.on(repaidEvent(true, BigDecimal.ZERO, BigDecimal.ZERO));

        // 余额取还款后快照整值覆盖，结清标记由 settled 转 0/1
        assertEquals(0, BigDecimal.ZERO.compareTo(existing.getOutstandingPrincipal()));
        assertEquals(0, BigDecimal.ZERO.compareTo(existing.getUnpaidInterest()));
        assertEquals(1, existing.getRepaid());
        verify(viewRepository).save(existing);
    }

    @Test
    void shouldNotResetBalanceWhenDisbursementEventReplayed() {
        // g04-04 回归：DLQ 重试会重放放款事件，创建期初值只在「新建行」这一刻写入 ——
        // 若挪回 MapStruct 常量赋值，会把已还款行的余额与结清标记重置回初始值
        PolicyLoanView repaidRow = newLoanRow(new BigDecimal("49911.10"), new BigDecimal("11.10"), 0);
        when(viewRepository.findById(LOAN_ID)).thenReturn(Optional.of(repaidRow));

        handler.on(disbursedEvent());

        assertEquals(0, new BigDecimal("49911.10").compareTo(repaidRow.getOutstandingPrincipal()));
        assertEquals(0, new BigDecimal("11.10").compareTo(repaidRow.getUnpaidInterest()));
        assertEquals(0, repaidRow.getRepaid());
    }

    @Test
    void shouldRejectRepayEventWithoutLoanRow() {
        when(viewRepository.findById(LOAN_ID)).thenReturn(Optional.empty());

        // 同一聚合事件按序投影，放款必先于还款；缺行属投影断裂，须显式失败而非静默跳过
        assertThrows(IllegalStateException.class,
                () -> handler.on(repaidEvent(false, new BigDecimal("49911.10"), BigDecimal.ZERO)));
    }

    @Test
    void shouldUpdateExistingRowWithoutResettingAuditTime() {
        PolicyLoanView existing = new PolicyLoanView();
        existing.setId(LOAN_ID);
        existing.setCreateTime(LocalDateTime.of(2026, 1, 1, 0, 0));
        when(viewRepository.findById(LOAN_ID)).thenReturn(Optional.of(existing));

        handler.on(disbursedEvent());

        // 幂等 upsert：仅首次创建时写入 createTime，重投事件不重置
        assertEquals(LocalDateTime.of(2026, 1, 1, 0, 0), existing.getCreateTime());
        verify(viewRepository).save(existing);
    }

    private PolicyLoanView newLoanRow(BigDecimal outstandingPrincipal, BigDecimal unpaidInterest, int repaid) {
        PolicyLoanView view = new PolicyLoanView();
        view.setId(LOAN_ID);
        view.setOutstandingPrincipal(outstandingPrincipal);
        view.setUnpaidInterest(unpaidInterest);
        view.setRepaid(repaid);
        return view;
    }

    private PolicyLoanDisbursedEvent disbursedEvent() {
        return new PolicyLoanDisbursedEvent("LOAN:" + POLICY_ID, LOAN_ID, POLICY_ID,
                new BigDecimal("50000.00"), new BigDecimal("0.05"), LOAN_DATE, "op-1", TENANT_ID);
    }

    private PolicyLoanRepaidEvent repaidEvent(boolean settled, BigDecimal principalAfter,
            BigDecimal interestAfter) {
        return new PolicyLoanRepaidEvent("LOAN:" + POLICY_ID, POLICY_ID, LOAN_ID, "REPAY-0001",
                new BigDecimal("50000.00"), new BigDecimal("250.00"), new BigDecimal("50000.00"),
                new BigDecimal("250.00"), principalAfter, interestAfter, settled, REPAY_DATE,
                "op-1", TENANT_ID);
    }
}
