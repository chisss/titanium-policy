package com.titanium.policy.service;

/**
 * VIN（车辆识别代号）格式校验器（纯领域服务）
 * <p>
 * 按 ISO 3779/3780 校验 VIN 法定编码：<b>17 位</b>、字符集为数字与大写字母但<b>排除 I/O/Q</b>
 * （法定不得出现，防与 1/0 混淆）、<b>第 9 位为校验位</b>（各位按权重加权求和后对 11 取模，
 * 余 10 记 {@code X}）。校验位不符即判定非法——模糊输入（少录/多录/抄错一位）几乎必然破坏
 * 校验位，这是 VIN 校验相对「非空 + 长度」的核心增量。
 * </p>
 * <p>
 * 🔴 <b>大小写口径</b>：VIN 法定为大写，但录入环节小写是常见习惯而非数据错误，故输入先
 * {@code trim} 再转大写后校验——不因大小写形态拒绝（转大写后含 {@code i/o/q} 者仍按非法字符拒绝）。
 * </p>
 * <p>
 * 🔴 <b>与调用方的分工</b>：本接口只回答「格式是否合法」（纯算法，无错误码、无中文文案），
 * 拒绝时携带哪个错误码、带什么参数、归属哪个险种段，由调用方
 * （{@code IssuanceEligibilityDomainServiceImpl}）编排——与 {@code ProductIssueRules} 相同的
 * 「算法与编排分离」原则（红线 15：领域层不拼句子）。
 * </p>
 * <p>
 * 🔴 <b>为何独立成组件而非内聚到 ProductIssueRules 或静态工具类</b>：产品规则承载的是
 * 「区间与清单」（{@code minAge}/{@code forbiddenOccupations} 等，因产品而异），而 VIN 编码是
 * <b>法定固定算法</b>（全部车险产品同一套，与产品配置无关）；编码算法是多步过程（长度 → 字符集
 * → 校验位），封装为类便于独立演化与测试。本服务符合「三无 + 一不属于」判据（无 CommandGateway、
 * 无外部 Port、无基础设施依赖，且不属于任何单个聚合根），可脱离 Spring 用 {@code new} 直测。
 * </p>
 */
public interface VinValidator {

    /**
     * 校验 VIN 格式是否合法（ISO 3779）。
     *
     * @param vin 车辆识别代号（允许小写与首尾空白，校验内部归一化）
     * @return 合法返回 {@code true}；{@code null}、长度不符、含非法字符（I/O/Q 或符号）
     *         或校验位不符返回 {@code false}
     */
    boolean isValid(String vin);
}
