package com.titanium.policy.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Service;

/**
 * 保单号生成器
 * <p>
 * 规则：POL + yyyyMMdd + 6位流水号，如 POL202602110001001
 * </p>
 */
@Service
public class PolicyNoGenerator {

    private static final String POLICY_PREFIX = "POL";
    private static final String INSURANCE_PREFIX = "INS";
    private static final String PROPOSAL_PREFIX = "PRP";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    private final AtomicLong sequence = new AtomicLong(1);

    /**
     * 生成保单号
     */
    public String generatePolicyNo() {
        return POLICY_PREFIX + LocalDate.now().format(DATE_FORMATTER) + String.format("%07d", sequence.getAndIncrement());
    }

    /**
     * 生成投保单号
     */
    public String generateInsuranceNo() {
        return INSURANCE_PREFIX + LocalDate.now().format(DATE_FORMATTER) + String.format("%07d", sequence.getAndIncrement());
    }

    /**
     * 生成意向单号
     */
    public String generateProposalNo() {
        return PROPOSAL_PREFIX + LocalDate.now().format(DATE_FORMATTER) + String.format("%07d", sequence.getAndIncrement());
    }
}
