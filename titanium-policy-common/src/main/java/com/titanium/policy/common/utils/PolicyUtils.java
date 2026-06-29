package com.titanium.policy.common.utils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 保单工具类
 */
public class PolicyUtils {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HHmmssSSS");

    /**
     * 生成唯一的保单号 格式: 年份(4位) + 月份(2位) + 日期(2位) + 时间(9位) + 随机数(6位)
     *
     * @return 唯一的保单号
     */
    public static String generatePolicyNumber() {
        LocalDateTime now = LocalDateTime.now();
        String datePart = DATE_FORMATTER.format(now);
        String timePart = TIME_FORMATTER.format(now);
        String randomPart = UUID.randomUUID().toString().substring(0, 6).toUpperCase();

        return datePart + timePart + randomPart;
    }

    /**
     * 验证保单号格式是否正确
     *
     * @param policyNumber 保单号
     * @return 是否有效
     */
    public static boolean isValidPolicyNumber(String policyNumber) {
        if (policyNumber == null || policyNumber.length() != 23) {
            return false;
        }

        // 检查前8位是否为数字
        try {
            Long.parseLong(policyNumber.substring(0, 8));
            Long.parseLong(policyNumber.substring(8, 17));
        } catch (NumberFormatException e) {
            return false;
        }

        return true;
    }

    /**
     * 生成唯一的保单ID
     *
     * @return 唯一的保单ID
     */
    public static String generatePolicyId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
