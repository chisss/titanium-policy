package com.titanium.policy.query.handler.projection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.titanium.metadata.enums.product.ProductEnum.ProductCategory;
import com.titanium.policy.entity.policy.PolicyProduct;
import com.titanium.policy.event.PolicyCreatedEvent;
import com.titanium.policy.query.repository.PolicyClauseViewRepository;
import com.titanium.policy.query.repository.PolicyCoverageViewRepository;
import com.titanium.policy.query.repository.PolicyProductViewRepository;
import com.titanium.policy.query.repository.PolicySubjectViewRepository;
import com.titanium.policy.query.view.PolicyProductView;
import com.titanium.policy.query.view.PolicyView;
import com.titanium.policy.valueobject.policy.LineCoveragePeriod;

/**
 * 段级读模型投影测试（G11/g11-04 D2 / AC-11）：等待期/犹豫期由**段**承载——交强 + 商业同单时
 * 两主险段取值本不相同，逐段落列方能按段读回本段值。
 */
class PolicyLineProjectionEventHandlerTest {

    private static final String POLICY_ID = "POLICY_001";
    private static final String TENANT_ID = "TENANT_001";
    private static final LocalDateTime PERIOD_START = LocalDateTime.of(2026, 9, 1, 0, 0);
    private static final LocalDateTime PERIOD_END = LocalDateTime.of(2027, 9, 1, 0, 0);

    /**
     * 🔴 AC-11 D2 正例：两个主险段各自读回**本段**等待期/犹豫期——交强无等待期（0 / null），
     * 商业等待期 90 天、犹豫期 15 天。若投影退回「保单级一份」写法，两段必有一段读回他段值。
     */
    @Test
    void projectsWaitingAndHesitationPeriodOfEachMainLine() {
        PolicyProductViewRepository productRepository = mock(PolicyProductViewRepository.class);
        when(productRepository.findById(org.mockito.ArgumentMatchers.anyString())).thenReturn(Optional.empty());

        new PolicyLineProjectionEventHandler(productRepository, mock(PolicyClauseViewRepository.class),
                mock(PolicySubjectViewRepository.class), mock(PolicyCoverageViewRepository.class))
                .on(eventWithTwoMainLines());

        ArgumentCaptor<PolicyProductView> captor = ArgumentCaptor.forClass(PolicyProductView.class);
        verify(productRepository, times(2)).save(captor.capture());
        Map<String, PolicyProductView> byLine = captor.getAllValues().stream()
                .collect(Collectors.toMap(PolicyProductView::getPolicyProductId, Function.identity()));

        PolicyProductView compulsory = byLine.get("LINE_1");
        assertEquals(0, compulsory.getWaitingPeriodDays(), "交强段无等待期");
        assertNull(compulsory.getWaitingPeriodEndDate());

        PolicyProductView commercial = byLine.get("LINE_2");
        assertEquals(90, commercial.getWaitingPeriodDays(), "商业段读回本段等待期");
        assertEquals(PERIOD_START.plusDays(90), commercial.getWaitingPeriodEndDate());
        assertEquals(15, commercial.getHesitationPeriodDays(), "商业段读回本段犹豫期");
        assertEquals(PERIOD_START.plusDays(15), commercial.getHesitationPeriodEndDate());
    }

    /**
     * 🔴 AC-11 D2 反向断言：保单级读模型**不得**再持有等待期/犹豫期列——D2 裁决要求二者下沉段级，
     * 若有人把段级要素塞回保单级（旧形态复活），本用例即红。
     */
    @Test
    void policyViewHasNoPolicyLevelGracePeriodColumns() {
        assertThrows(NoSuchFieldException.class, () -> PolicyView.class.getDeclaredField("waitingPeriodDays"));
        assertThrows(NoSuchFieldException.class, () -> PolicyView.class.getDeclaredField("waitingPeriodEndDate"));
        assertThrows(NoSuchFieldException.class, () -> PolicyView.class.getDeclaredField("hesitationPeriodDays"));
        assertThrows(NoSuchFieldException.class, () -> PolicyView.class.getDeclaredField("hesitationPeriodEndDate"));
    }

    /** 两主险段出单事件：交强（无宽限期）+ 商业（等待期 90 / 犹豫期 15）。 */
    private PolicyCreatedEvent eventWithTwoMainLines() {
        PolicyProduct compulsory = mainLine("LINE_1", 1, "P_CMP",
                LineCoveragePeriod.fixedTerm(PERIOD_START, PERIOD_END, null, null));
        PolicyProduct commercial = mainLine("LINE_2", 2, "P_CMR",
                LineCoveragePeriod.fixedTerm(PERIOD_START, PERIOD_END, null, null).withGracePeriods(90, 15));
        return new PolicyCreatedEvent(POLICY_ID, null, null, null, null, null, null,
                "BIZ_001", null, null, null, null, List.of(compulsory, commercial), null, null, null, null, null,
                null, TENANT_ID);
    }

    private static PolicyProduct mainLine(String policyProductId, int lineNo, String productCode,
                                          LineCoveragePeriod coveragePeriod) {
        return new PolicyProduct(policyProductId, lineNo, ProductCategory.MAIN, null, "PRODUCT_" + policyProductId,
                productCode, "测试主险", "V1.0", "PLAN_V1", null, null, null, coveragePeriod, null, null, null,
                List.of(), List.of(), List.of(), null);
    }
}
