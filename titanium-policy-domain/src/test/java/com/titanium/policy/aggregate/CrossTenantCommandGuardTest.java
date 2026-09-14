package com.titanium.policy.aggregate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.axonframework.test.aggregate.AggregateTestFixture;
import org.axonframework.test.aggregate.FixtureConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.titanium.metadata.enums.billing.BillingEnum.PaymentMethod;
import com.titanium.metadata.enums.policy.PolicyForm;
import com.titanium.metadata.valueobject.Money;
import com.titanium.policy.command.CancelPolicyCommand;
import com.titanium.policy.command.MaturePolicyCommand;
import com.titanium.policy.command.RecordPremiumCollectionCommand;
import com.titanium.policy.command.SuspendPolicyCommand;
import com.titanium.policy.command.TerminatePolicyCommand;
import com.titanium.policy.command.WaivePremiumCommand;
import com.titanium.policy.common.enums.PolicyStatusCode;
import com.titanium.policy.common.enums.PremiumWaiverReason;
import com.titanium.policy.event.PolicyCreatedEvent;
import com.titanium.policy.exception.PolicyBusinessRuleException;
import com.titanium.policy.valueobject.policy.PolicyNo;
import com.titanium.policy.valueobject.policy.PolicyPeriod;
import com.titanium.policy.valueobject.policy.PolicyStatus;

/**
 * 跨租户写入拒绝守护测试（C-02 残留）。
 * <p>
 * <b>威胁模型</b>：Axon 按聚合标识装载、事件流不按租户隔离；命令携带的 {@code tenantId} 来自调用方请求头
 * （{@code X-Tenant-Id}），完全可被伪造。若聚合不校验，持他租户的聚合标识即可跨租户写入——
 * 保单域的终止/退保/豁免等均不可逆。
 * </p>
 * <p>
 * 本测试以两条互补的判据守护：
 * </p>
 * <ol>
 * <li><b>行为判据</b>：代表性命令以错租户/空租户派发时，聚合必须抛业务异常且<b>不产生任何事件</b>。</li>
 * <li><b>结构判据</b>：扫描三个聚合根源码，每个 {@code @CommandHandler} 命令方法的<b>首条语句</b>必须是
 *     {@code requireSameTenant(command.tenantId());}——新增处理器漏写守护即失败，
 *     无需为每条命令各写一个夹具。{@code @CommandHandler} 标注的<b>创建构造器</b>（Policy 2 + Insurance 2
 *     + Proposal 1）不适用：创建时尚无「聚合既有租户」可比对，其租户由创建事件的 {@code tenantId} 落库，
 *     后续命令回放该事件后即受守护。</li>
 * </ol>
 *
 * @see com.titanium.metadata.errorcode.PolicyErrorCode#POLICY_NOT_EXIST
 */
class CrossTenantCommandGuardTest {

    private static final String POLICY_ID = "policy-ct-001";
    private static final String TENANT_ID = "tenant-001";
    /** 非本聚合归属的租户 */
    private static final String OTHER_TENANT_ID = "tenant-002";

    /** 聚合根源码相对模块根的路径（surefire 以模块目录为工作目录） */
    private static final List<String> AGGREGATE_SOURCES = List.of(
            "Policy.java", "Insurance.java", "Proposal.java");

    private static final String GUARD_STATEMENT = "requireSameTenant(command.tenantId());";

    private FixtureConfiguration<Policy> fixture;

    @BeforeEach
    void setUp() {
        fixture = new AggregateTestFixture<>(Policy.class);
        // Policy 的事件回放含 LocalDateTime.now() 等非确定性赋值，关闭非法状态变更检测以聚焦租户守护
        fixture.setReportIllegalStateChange(false);
    }

    // ==================== 行为判据：代表性命令的跨租户拒绝 ====================

    @Test
    @DisplayName("终止保单：错租户被拒且不产生事件")
    void shouldRejectTerminateFromOtherTenant() {
        assertCrossTenantRejected(new TerminatePolicyCommand(
                POLICY_ID, "退保", "op-1", null, OTHER_TENANT_ID));
    }

    @Test
    @DisplayName("暂停保单：错租户被拒")
    void shouldRejectSuspendFromOtherTenant() {
        assertCrossTenantRejected(new SuspendPolicyCommand(POLICY_ID, "欠费", "op-1", OTHER_TENANT_ID));
    }

    @Test
    @DisplayName("撤销保单：错租户被拒")
    void shouldRejectCancelFromOtherTenant() {
        assertCrossTenantRejected(new CancelPolicyCommand(POLICY_ID, "撤销", "op-1", OTHER_TENANT_ID));
    }

    @Test
    @DisplayName("满期给付：错租户被拒")
    void shouldRejectMatureFromOtherTenant() {
        assertCrossTenantRejected(new MaturePolicyCommand(
                POLICY_ID, new BigDecimal("10000.00"), "op-1", OTHER_TENANT_ID));
    }

    @Test
    @DisplayName("保费豁免：错租户被拒")
    void shouldRejectWaivePremiumFromOtherTenant() {
        assertCrossTenantRejected(new WaivePremiumCommand(
                POLICY_ID, PremiumWaiverReason.POLICY_HOLDER_DEATH, "op-1", OTHER_TENANT_ID));
    }

    @Test
    @DisplayName("实收登记：错租户被拒（资金类命令不因金额合法而放行）")
    void shouldRejectPremiumCollectionFromOtherTenant() {
        assertCrossTenantRejected(new RecordPremiumCollectionCommand(
                POLICY_ID, "pay-1", "PAY-20260911-001",
                Money.of(new BigDecimal("1000.00"), "CNY"), PaymentMethod.MONTHLY,
                LocalDateTime.now(), "op-1", OTHER_TENANT_ID));
    }

    @Test
    @DisplayName("空租户亦被拒（失败关闭，不因缺租户而降级放行）")
    void shouldRejectBlankTenant() {
        assertCrossTenantRejected(new TerminatePolicyCommand(POLICY_ID, "退保", "op-1", null, null));
        assertCrossTenantRejected(new TerminatePolicyCommand(POLICY_ID, "退保", "op-1", null, "   "));
    }

    @Test
    @DisplayName("同租户不受影响（守护不得误伤正常路径）")
    void shouldStillAcceptSameTenant() {
        // 终止：状态不满足（NOT_EFFECTIVE 未激活）时另抛业务异常，故此处只断言「不因租户被拒」
        fixture.given(createdEvent())
                .when(new SuspendPolicyCommand(POLICY_ID, "欠费", "op-1", TENANT_ID))
                .expectException(PolicyBusinessRuleException.class);
    }

    private void assertCrossTenantRejected(Object command) {
        fixture.given(createdEvent())
                .when(command)
                .expectException(PolicyBusinessRuleException.class)
                .expectNoEvents();
    }

    // ==================== 结构判据：全处理器覆盖 ====================

    @Test
    @DisplayName("三个聚合根的每个 @CommandHandler 首条语句必须是跨租户守护")
    void everyCommandHandlerMustGuardTenantFirst() throws IOException {
        Path aggregateDir = resolveAggregateSourceDir();
        List<String> violations = new ArrayList<>();
        int inspected = 0;
        int constructors = 0;

        for (String fileName : AGGREGATE_SOURCES) {
            Path file = aggregateDir.resolve(fileName);
            assertTrue(Files.isRegularFile(file), "聚合根源码不存在: " + file);
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            for (int i = 0; i < lines.size(); i++) {
                if (!"@CommandHandler".equals(lines.get(i).trim())) {
                    continue;
                }
                int signatureEnd = i + 1;
                while (signatureEnd < lines.size() && !lines.get(signatureEnd).trim().endsWith(") {")) {
                    signatureEnd++;
                }
                if (isCreationConstructor(lines.get(i + 1).trim(), fileName)) {
                    // 创建路径尚无「聚合既有租户」可比对；其租户由 creation 事件的 tenantId 落库，
                    // 后续命令回放该事件后即受守护——故构造器不适用本条判据，但须计数以防静默跳过
                    constructors++;
                    continue;
                }
                String firstStatement = firstStatementAfter(lines, signatureEnd + 1);
                inspected++;
                if (!GUARD_STATEMENT.equals(firstStatement)) {
                    violations.add(fileName + ":" + (signatureEnd + 1)
                            + " 首条语句为「" + firstStatement + "」，应为「" + GUARD_STATEMENT + "」");
                }
            }
        }

        assertEquals(List.of(), violations,
                "以下 @CommandHandler 未把跨租户守护放在首条语句：" + String.join("；", violations));
        // 守护一旦失效（例如正则或结构变更导致一个都没扫到）本测试必须失败，而非静默通过
        assertEquals(29, inspected, "扫描到的 @CommandHandler 数量异常，守护规则可能已失效");
        assertEquals(5, constructors, "扫描到的创建构造器数量异常（Policy 2 + Insurance 2 + Proposal 1）");
    }

    /** 判定该 {@code @CommandHandler} 是否标注在创建构造器上（方法名即聚合类名） */
    private boolean isCreationConstructor(String signatureLine, String fileName) {
        String typeName = fileName.substring(0, fileName.length() - ".java".length());
        return signatureLine.startsWith("public " + typeName + "(");
    }

    /** 返回首条非空、非注释语句（去缩进） */
    private String firstStatementAfter(List<String> lines, int from) {
        for (int i = from; i < lines.size(); i++) {
            String trimmed = lines.get(i).trim();
            if (trimmed.isEmpty() || trimmed.startsWith("//") || trimmed.startsWith("/*")
                    || trimmed.startsWith("*")) {
                continue;
            }
            return trimmed;
        }
        return fail("未找到方法体首条语句（自第 " + (from + 1) + " 行起）");
    }

    /** 自模块目录向上定位含聚合根源码的目录（surefire 与 IDE 的工作目录可能不同） */
    private Path resolveAggregateSourceDir() {
        String relative = "src/main/java/com/titanium/policy/aggregate";
        Path current = Paths.get("").toAbsolutePath();
        for (int depth = 0; depth < 6 && current != null; depth++) {
            Path candidate = current.resolve(relative);
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("未能定位聚合根源码目录，当前目录: " + Paths.get("").toAbsolutePath());
    }

    // ==================== 夹具 ====================

    private PolicyCreatedEvent createdEvent() {
        LocalDateTime now = LocalDateTime.now();
        return new PolicyCreatedEvent(POLICY_ID, new PolicyNo("POL-2026-0001"), PolicyForm.INDIVIDUAL, null,
                null, null, null, null, PolicyPeriod.of(now, now.plusYears(1), 0, 0), null, null,
                List.of(), null, null, null,
                new PolicyStatus(PolicyStatusCode.NOT_EFFECTIVE, now, "创建", "system"),
                null, null, TENANT_ID);
    }
}
