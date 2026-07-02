package com.titanium.policy.infrastructure.generator;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Component;

import com.titanium.policy.generator.EndorsementNoGenerator;

/**
 * 批单号生成器实现
 * <p>
 * 规则：ED + yyyyMMdd + 7位流水号，如 ED202606280000001。
 * 注：单机内存流水，生产多实例需替换为分布式发号（雪花/号段），与现有 PolicyNoGenerator 一致的待办。
 * </p>
 */
@Component
public class EndorsementNoGeneratorImpl implements EndorsementNoGenerator {

    private static final String            PREFIX         = "ED";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    private final AtomicLong               sequence       = new AtomicLong(1);

    @Override
    public String generate(String policyId) {
        return PREFIX + LocalDate.now().format(DATE_FORMATTER) + String.format("%07d", sequence.getAndIncrement());
    }
}
