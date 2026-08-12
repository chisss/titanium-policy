package com.titanium.policy.common.enums;

import com.titanium.metadata.enums.BaseEnum;

import lombok.Getter;

/**
 * 出单流程阶段枚举（保单域专属）
 * <p>
 * 出单是跨聚合的长流程（受理 → 校验 → 试算 → 建单 → 核保 → 收费 → 出单 → 生效），产出物随
 * 出单模式而异。本枚举表达「当前走到哪一步」，供调用方轮询进度并决定后续动作：
 * 待收费则引导支付、核保中则等待、已出单则展示保单。
 * </p>
 */
@Getter
public enum IssuanceStage implements BaseEnum {

    /** 已受理（幂等校验通过，进入流程） */
    ACCEPTED(1, "ACCEPTED", "已受理", "出单请求已受理，等待要素校验"),
    /** 要素校验中（依产品投保条件裁决年龄/保额/职业等） */
    VALIDATING(2, "VALIDATING", "要素校验中", "依产品投保条件校验投保要素"),
    /** 保费试算中 */
    QUOTING(3, "QUOTING", "保费试算中", "向计费域请求标准保费计算"),
    /** 意向单已创建（三步出单起点） */
    PROPOSAL_CREATED(4, "PROPOSAL_CREATED", "意向单已创建", "三步出单：意向单已建，待转投保单"),
    /** 投保单已创建（两步出单起点） */
    INSURANCE_CREATED(5, "INSURANCE_CREATED", "投保单已创建", "投保单已建，待提交核保"),
    /** 核保中（含自动核保与人工核保） */
    UNDERWRITING(6, "UNDERWRITING", "核保中", "已提交核保，等待核保结论"),
    /** 待收费（核保通过，等待保费收讫） */
    PENDING_COLLECTION(7, "PENDING_COLLECTION", "待收费", "核保通过，账单已开立，等待保费收讫"),
    /** 保单已出单（尚未生效） */
    POLICY_ISSUED(8, "POLICY_ISSUED", "保单已出单", "正式保单已创建，等待生效条件满足"),
    /** 保单已生效（流程完成） */
    POLICY_EFFECTIVE(9, "POLICY_EFFECTIVE", "保单已生效", "保单生效，出单流程完成"),
    /** 已拒绝（要素校验不通过或核保拒保，终态） */
    REJECTED(10, "REJECTED", "已拒绝", "要素校验不通过或核保拒保，流程终止");

    private final Integer enumCode;
    private final String  code;
    private final String  name;
    private final String  desc;

    IssuanceStage(Integer enumCode, String code, String name, String desc) {
        this.enumCode = enumCode;
        this.code = code;
        this.name = name;
        this.desc = desc;
    }

    /**
     * 按 code（枚举名称）反查出单阶段，未匹配返回 null。
     *
     * @param code 阶段编码
     * @return 匹配的枚举，未匹配返回 null
     */
    public static IssuanceStage fromCode(String code) {
        return BaseEnum.fromCode(IssuanceStage.class, code);
    }

    /**
     * 是否为终态（流程已结束，不再推进）。
     *
     * @return 终态返回 {@code true}
     */
    public boolean isTerminal() {
        return this == POLICY_EFFECTIVE || this == REJECTED;
    }

    /**
     * 是否需要调用方后续动作（去支付）。
     *
     * @return 待收费返回 {@code true}
     */
    public boolean requiresPayment() {
        return this == PENDING_COLLECTION;
    }
}
