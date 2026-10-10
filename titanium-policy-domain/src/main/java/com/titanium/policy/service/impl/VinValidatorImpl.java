package com.titanium.policy.service.impl;

import java.util.Locale;

import org.springframework.stereotype.Service;

import com.titanium.policy.service.VinValidator;

/**
 * VIN 格式校验器实现（ISO 3779/3780，纯算法）
 * <p>
 * 校验分三步、快速失败：<b>长度</b>（须 17 位）→ <b>字符集</b>（数字或大写字母，排除 I/O/Q）
 * → <b>校验位</b>（第 9 位）。字符集检查必须前置于校验位计算——I/O/Q 在 ISO 3779 换算表中
 * 无对应数值（法定排除的字符），跳过检查会在换算时落入不可达分支。
 * </p>
 * <p>
 * 换算表（{@link #transliterate}）与权重表（{@link #WEIGHTS}）为 ISO 3779 标准值，勿改动；
 * 校验位取模基数 11，余 10 记 {@code X}（{@link #CHECK_DIGIT_X}）。
 * </p>
 *
 * @author wei.sun
 * @since 2026/10/10
 * @see VinValidator 接口契约与大小写口径说明
 */
@Service
public class VinValidatorImpl implements VinValidator {

    /** VIN 法定长度（ISO 3779） */
    private static final int VIN_LENGTH = 17;

    /** 校验位所在下标（第 9 位，0 基） */
    private static final int CHECK_DIGIT_INDEX = 8;

    /** 取模基数（余数 10 记 {@code X}） */
    private static final int MODULUS = 11;

    /** 余数为 10 时的校验位字符 */
    private static final char CHECK_DIGIT_X = 'X';

    /** 各位置权重（ISO 3779 标准值，第 9 位自身权重 0；勿改动） */
    private static final int[] WEIGHTS = {8, 7, 6, 5, 4, 3, 2, 10, 0, 9, 8, 7, 6, 5, 4, 3, 2};

    @Override
    public boolean isValid(String vin) {
        if (vin == null) {
            return false;
        }
        // VIN 法定为大写；小写是录入习惯而非数据错误，归一化后再校验
        String normalized = vin.trim().toUpperCase(Locale.ROOT);
        if (normalized.length() != VIN_LENGTH) {
            return false;
        }
        if (!hasOnlyLegalCharacters(normalized)) {
            return false;
        }
        return checkDigitMatches(normalized);
    }

    /**
     * 字符集检查：数字与大写字母，排除 I/O/Q（ISO 3779 法定不得出现）。
     */
    private static boolean hasOnlyLegalCharacters(String vin) {
        for (int i = 0; i < VIN_LENGTH; i++) {
            char c = vin.charAt(i);
            boolean digit = c >= '0' && c <= '9';
            boolean legalLetter = c >= 'A' && c <= 'Z' && c != 'I' && c != 'O' && c != 'Q';
            if (!digit && !legalLetter) {
                return false;
            }
        }
        return true;
    }

    /**
     * 校验位比对：按权重加权求和、对 11 取模，与第 9 位比对。
     * <p>
     * 前置检查已保证长度与字符集合法，故此处 {@link #transliterate} 的换算表必然命中。
     * </p>
     */
    private static boolean checkDigitMatches(String vin) {
        int sum = 0;
        for (int i = 0; i < VIN_LENGTH; i++) {
            sum += transliterate(vin.charAt(i)) * WEIGHTS[i];
        }
        int remainder = sum % MODULUS;
        char expected = remainder == 10 ? CHECK_DIGIT_X : (char) ('0' + remainder);
        return vin.charAt(CHECK_DIGIT_INDEX) == expected;
    }

    /**
     * 字符 → 数值换算（ISO 3779 标准表）。
     * <p>
     * 标准表按字母段分布：A-H=1-8、J-N=1-5、P=7、R=9、S-Z=2-9（U=4 合法属 S-Z 段；
     * I/O/Q 不在此表——前置字符集检查已排除，{@code default} 分支不可达）。
     * </p>
     */
    private static int transliterate(char c) {
        if (c >= '0' && c <= '9') {
            return c - '0';
        }
        return switch (c) {
            case 'A', 'J' -> 1;
            case 'B', 'K', 'S' -> 2;
            case 'C', 'L', 'T' -> 3;
            case 'D', 'M', 'U' -> 4;
            case 'E', 'N', 'V' -> 5;
            case 'F', 'W' -> 6;
            case 'G', 'P', 'X' -> 7;
            case 'H', 'Y' -> 8;
            case 'R', 'Z' -> 9;
            // 不可达：字符集检查先行，此处字符必然在换算表内
            default -> throw new IllegalStateException("VIN 字符集检查与换算表不一致（不可达分支被触发）: " + c);
        };
    }
}
