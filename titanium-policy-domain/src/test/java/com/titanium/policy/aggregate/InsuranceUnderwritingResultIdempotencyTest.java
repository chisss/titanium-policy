package com.titanium.policy.aggregate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.axonframework.test.aggregate.AggregateTestFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.titanium.metadata.enums.policy.PolicyForm;
import com.titanium.metadata.enums.underwriting.UnderwritingEnum.ConclusionType;
import com.titanium.policy.command.ReceiveUnderwritingResultCommand;
import com.titanium.policy.event.insurance.InsuranceCreatedEvent;
import com.titanium.policy.event.insurance.InsuranceSubmittedForUnderwritingEvent;
import com.titanium.policy.event.insurance.UnderwritingResultReceivedEvent;
import com.titanium.policy.exception.PolicyBusinessRuleException;
import com.titanium.policy.valueobject.insurance.UnderwritingResult;

/**
 * 核保结论重投的幂等回归测试（D-501-15）。
 * <p>
 * 现场：policy 消费 {@code underwriting-decided} 时，若投保单已不在核保中状态，聚合抛
 * {@code Only applications in underwriting can receive results}；Spring Kafka 默认错误处理器
 * 零间隔重试 10 次后耗尽并<b>跳过</b>该消息（LAG=0、仅留 ERROR 日志），消息内容永久丢失。
 * </p>
 * <p>
 * 本测试锁死修复后的边界：<b>同一核保单号 + 同一结论</b> = 重投 ⇒ 幂等成功（不发事件）；
 * 其余非核保中状态下的投递仍须抛异常暴露，<b>不得</b>被幂等分支无差别吞掉。
 * </p>
 */
class InsuranceUnderwritingResultIdempotencyTest {

    private static final String INSURANCE_ID   = "INSURANCE_001";
    private static final String TENANT_ID      = "TENANT_001";
    private static final String UNDERWRITING_ID = "UW_001";

    @Test
    @DisplayName("同一结论重复投递：幂等成功，不再发事件也不改状态")
    void shouldTreatRedeliveredSameConclusionAsIdempotent() {
        UnderwritingResult accepted = result(ConclusionType.ACCEPT);

        fixture()
                .given(createdEvent(), submittedEvent(), receivedEvent(result(ConclusionType.ACCEPT)))
                .when(command(accepted))
                .expectNoEvents()
                .expectSuccessfulHandlerExecution();
    }

    @Test
    @DisplayName("被拒结论重复投递同样幂等（幂等判定不依赖具体结论）")
    void shouldTreatRedeliveredRejectionAsIdempotent() {
        fixture()
                .given(createdEvent(), submittedEvent(), receivedEvent(result(ConclusionType.REJECT)))
                .when(command(result(ConclusionType.REJECT)))
                .expectNoEvents()
                .expectSuccessfulHandlerExecution();
    }

    @Test
    @DisplayName("同核保单号但结论被改判：仍须抛异常，不得按重投静默吞掉")
    void shouldRejectSameUnderwritingIdWithDifferentConclusion() {
        fixture()
                .given(createdEvent(), submittedEvent(), receivedEvent(result(ConclusionType.ACCEPT)))
                .when(command(result(ConclusionType.REJECT)))
                .expectException(PolicyBusinessRuleException.class)
                .expectNoEvents();
    }

    @Test
    @DisplayName("换了新的核保单号（重新核保）：仍须抛异常")
    void shouldRejectDifferentUnderwritingId() {
        UnderwritingResult anotherDecision = new UnderwritingResult("UW_002", ConclusionType.ACCEPT, "复审通过",
                "UW_002", LocalDateTime.now(), null, null);

        fixture()
                .given(createdEvent(), submittedEvent(), receivedEvent(result(ConclusionType.ACCEPT)))
                .when(command(anotherDecision))
                .expectException(PolicyBusinessRuleException.class)
                .expectNoEvents();
    }

    @Test
    @DisplayName("从未进入核保即收到结论：仍须抛异常（不因结论为空表而被误判为重投）")
    void shouldRejectResultWhenNeverSubmittedForUnderwriting() {
        fixture()
                .given(createdEvent())
                .when(command(result(ConclusionType.ACCEPT)))
                .expectException(PolicyBusinessRuleException.class)
                .expectNoEvents();
    }

    @Test
    @DisplayName("仍在核保中：正常接收并发事件（幂等分支不得误伤首投）")
    void shouldStillAcceptFirstDeliveryWhileUnderwriting() {
        UnderwritingResult accepted = result(ConclusionType.ACCEPT);

        fixture()
                .given(createdEvent(), submittedEvent())
                .when(command(accepted))
                .expectEvents(receivedEvent(accepted));
    }

    // ==================== 夹具 ====================

    /**
     * 构造测试夹具。
     * <p>
     * 🔴 关闭「非法状态变更」自检（{@code setReportIllegalStateChange(false)}）：该校验会比较
     * 「命令处理后的聚合状态」与「重放给定事件后的聚合状态」是否相等，而 {@code Insurance.on(
     * UnderwritingResultReceivedEvent)} 内的 {@code status.transitionStatus(...)} 用
     * {@code LocalDateTime.now()} 打时间戳，两次重放必然相差毫秒级，从而<b>恒定误报</b>
     * （状态码与变更原因均相同，仅 {@code statusTime} 不同）。
     * </p>
     * <p>
     * ⚠️ 这是本聚合既有的非确定性（状态时间戳不来自事件载荷），非本次修复引入；此处<b>局部</b>关闭
     * 该自检以免误报掩盖真实断言。行为契约仍由 {@code expectEvents}/{@code expectNoEvents}/
     * {@code expectException} 逐一锁定，不受影响。
     * </p>
     */
    private AggregateTestFixture<Insurance> fixture() {
        AggregateTestFixture<Insurance> fixture = new AggregateTestFixture<>(Insurance.class);
        fixture.setReportIllegalStateChange(false);
        return fixture;
    }

    private ReceiveUnderwritingResultCommand command(UnderwritingResult result) {
        return new ReceiveUnderwritingResultCommand(INSURANCE_ID, result, TENANT_ID);
    }

    private UnderwritingResult result(ConclusionType conclusion) {
        return new UnderwritingResult(UNDERWRITING_ID, conclusion, "核保意见", "UW_001", LocalDateTime.now(), null,
                null);
    }

    /**
     * 由核保结论装配事件。
     * <p>
     * 🔴 用同一 {@link UnderwritingResult} 实例同时充当「给定事件」与「期望事件」的来源：事件载荷
     * 逐字段透传结论（含 {@code underwritingTime}），若两侧各自 {@code now()} 打时间戳必然失配。
     * </p>
     */
    private UnderwritingResultReceivedEvent receivedEvent(UnderwritingResult result) {
        return new UnderwritingResultReceivedEvent(INSURANCE_ID, result.underwritingId(), result.resultCode(),
                result.underwritingOpinion(), result.underwriterId(), result.underwritingTime(), result.condition(),
                TENANT_ID, result.extraPremiumRatio(), null);
    }

    private InsuranceSubmittedForUnderwritingEvent submittedEvent() {
        LocalDateTime now = LocalDateTime.now();
        return new InsuranceSubmittedForUnderwritingEvent(INSURANCE_ID, "INS_001", "HOLDER_001", 1,
                new BigDecimal("1000.00"), "CNY", now, now.plusYears(1), List.of("PRODUCT_001"), 0,
                PolicyForm.INDIVIDUAL, TENANT_ID, null);
    }

    private InsuranceCreatedEvent createdEvent() {
        LocalDateTime now = LocalDateTime.now();
        return new InsuranceCreatedEvent(INSURANCE_ID, "INS_001", null, PolicyForm.INDIVIDUAL, "HOLDER_001", 1,
                new BigDecimal("1000.00"), now, now.plusYears(1), List.of(), 0, null, null, null, null, null, null,
                now, TENANT_ID, null, null, 0);
    }
}
