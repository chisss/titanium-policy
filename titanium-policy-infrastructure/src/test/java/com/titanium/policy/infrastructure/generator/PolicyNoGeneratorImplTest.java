package com.titanium.policy.infrastructure.generator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

/**
 * 持久化发号器契约测试。
 * <p>
 * 测试存储模拟数据库唯一键与原子 upsert；两次生成器实例代表两个应用实例，重建实例代表进程重启。
 * </p>
 */
class PolicyNoGeneratorImplTest {

    private static final LocalDate FIRST_DAY = LocalDate.of(2026, 8, 13);
    private static final Clock      FIRST_CLOCK = Clock.fixed(
            FIRST_DAY.atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant(), ZoneId.of("Asia/Shanghai"));

    @Test
    void twoGeneratorInstancesShareOneSequenceWithoutDuplicates() throws Exception {
        SharedAtomicSequenceStore store = new SharedAtomicSequenceStore();
        PolicyNoGeneratorImpl first = new PolicyNoGeneratorImpl(store, FIRST_CLOCK);
        PolicyNoGeneratorImpl second = new PolicyNoGeneratorImpl(store, FIRST_CLOCK);
        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            List<Future<String>> futures = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                PolicyNoGeneratorImpl generator = i % 2 == 0 ? first : second;
                futures.add(executor.submit(() -> generator.generatePolicyNo("TENANT_A")));
            }
            Set<String> numbers = futures.stream().map(this::get).collect(Collectors.toSet());
            assertEquals(100, numbers.size());
            assertTrue(numbers.contains("POL202608130000001"));
            assertTrue(numbers.contains("POL202608130000100"));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void recreatedGeneratorContinuesExistingSequence() {
        SharedAtomicSequenceStore store = new SharedAtomicSequenceStore();
        PolicyNoGeneratorImpl first = new PolicyNoGeneratorImpl(store, FIRST_CLOCK);
        assertEquals("POL202608130000001", first.generatePolicyNo("TENANT_A"));

        PolicyNoGeneratorImpl recreated = new PolicyNoGeneratorImpl(store, FIRST_CLOCK);
        assertEquals("POL202608130000002", recreated.generatePolicyNo("TENANT_A"));
    }

    @Test
    void dateTenantAndDocumentTypeAreIndependent() {
        SharedAtomicSequenceStore store = new SharedAtomicSequenceStore();
        PolicyNoGeneratorImpl today = new PolicyNoGeneratorImpl(store, FIRST_CLOCK);
        Clock nextDayClock = Clock.fixed(FIRST_DAY.plusDays(1).atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant(),
                ZoneId.of("Asia/Shanghai"));
        PolicyNoGeneratorImpl tomorrow = new PolicyNoGeneratorImpl(store, nextDayClock);

        assertEquals("POL202608130000001", today.generatePolicyNo("TENANT_A"));
        assertEquals("INS202608130000001", today.generateInsuranceNo("TENANT_A"));
        assertEquals("PRP202608130000001", today.generateProposalNo("TENANT_A"));
        assertEquals("POL202608130000001", today.generatePolicyNo("TENANT_B"));
        assertEquals("POL202608140000001", tomorrow.generatePolicyNo("TENANT_A"));
        assertNotEquals(today.generateInsuranceNo("TENANT_A"), today.generateProposalNo("TENANT_A"));
    }

    @Test
    void rejectsBlankTenantIdBeforePersisting() {
        SharedAtomicSequenceStore store = new SharedAtomicSequenceStore();
        PolicyNoGeneratorImpl generator = new PolicyNoGeneratorImpl(store, FIRST_CLOCK);
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> generator.generatePolicyNo("  "));
        assertTrue(store.values().isEmpty());
    }

    private String get(Future<String> future) {
        try {
            return future.get();
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    /** 模拟 t_policy_number_sequence 的唯一键 + 原子 upsert。 */
    private static final class SharedAtomicSequenceStore implements PolicyNoSequenceStore {

        private final Map<Key, Long> sequences = new HashMap<>();

        @Override
        public synchronized long next(String tenantId, String documentType, LocalDate businessDate) {
            Key key = new Key(tenantId, documentType, businessDate);
            long next = sequences.getOrDefault(key, 0L) + 1;
            sequences.put(key, next);
            return next;
        }

        Map<Key, Long> values() {
            return Map.copyOf(sequences);
        }

        private record Key(String tenantId, String documentType, LocalDate businessDate) {
        }
    }
}
