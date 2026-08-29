package com.titanium.policy.infrastructure.generator;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.titanium.common.util.BusinessNumberFormatter;
import com.titanium.policy.generator.PolicyNoGenerator;

/**
 * 基于持久化流水的业务编号生成器。
 * <p>
 * 业务日期、租户和单据类型共同构成序列隔离键；序列状态不保存在 JVM 中，容器重启和多实例部署
 * 均复用同一数据库原子预占结果。
 * </p>
 */
@Service
public class PolicyNoGeneratorImpl implements PolicyNoGenerator {

    private final PolicyNoSequenceStore sequenceStore;
    private final Clock                clock;

    @Autowired
    public PolicyNoGeneratorImpl(PolicyNoSequenceStore sequenceStore) {
        this(sequenceStore, Clock.systemDefaultZone());
    }

    /**
     * 可注入时钟的构造器，供日期切换测试和固定业务日期的运行环境使用。
     *
     * @param sequenceStore 持久化流水存储
     * @param clock 业务日期时钟
     */
    public PolicyNoGeneratorImpl(PolicyNoSequenceStore sequenceStore, Clock clock) {
        this.sequenceStore = Objects.requireNonNull(sequenceStore, "sequenceStore");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public String generatePolicyNo(String tenantId) {
        return generate("POL", "POLICY", tenantId);
    }

    @Override
    public String generateInsuranceNo(String tenantId) {
        return generate("INS", "INSURANCE", tenantId);
    }

    @Override
    public String generateProposalNo(String tenantId) {
        return generate("PRP", "PROPOSAL", tenantId);
    }

    private String generate(String prefix, String documentType, String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId 不能为空");
        }
        LocalDate businessDate = LocalDate.now(clock);
        long sequence = sequenceStore.next(tenantId, documentType, businessDate);
        try {
            return BusinessNumberFormatter.format(prefix, businessDate, sequence);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("业务编号流水超出 7 位范围: " + sequence, exception);
        }
    }
}
