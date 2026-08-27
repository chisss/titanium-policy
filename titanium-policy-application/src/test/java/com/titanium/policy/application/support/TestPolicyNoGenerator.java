package com.titanium.policy.application.support;

import java.util.concurrent.atomic.AtomicLong;

import com.titanium.policy.generator.PolicyNoGenerator;

/**
 * 应用层流程测试用的确定性发号桩，不代表生产实现。
 */
public final class TestPolicyNoGenerator implements PolicyNoGenerator {

    private final AtomicLong policySequence = new AtomicLong();
    private final AtomicLong insuranceSequence = new AtomicLong();
    private final AtomicLong proposalSequence = new AtomicLong();

    @Override
    public String generatePolicyNo(String tenantId) {
        return format("POL", policySequence.incrementAndGet());
    }

    @Override
    public String generateInsuranceNo(String tenantId) {
        return format("INS", insuranceSequence.incrementAndGet());
    }

    @Override
    public String generateProposalNo(String tenantId) {
        return format("PRP", proposalSequence.incrementAndGet());
    }

    private String format(String prefix, long sequence) {
        return prefix + "20260813" + String.format("%07d", sequence);
    }
}
