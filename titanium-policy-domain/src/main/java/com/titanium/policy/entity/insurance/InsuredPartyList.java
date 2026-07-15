package com.titanium.policy.entity.insurance;

import java.util.ArrayList;
import java.util.List;

import com.titanium.metadata.enums.customer.CustomerEnum.CustomerGender;
import com.titanium.metadata.enums.customer.CustomerEnum.IdCardType;
import com.titanium.metadata.enums.customer.CustomerEnum.InsuranceRole;
import com.titanium.policy.common.enums.FamilyRelation;

/**
 * 投保参与方清单实体
 * <p>
 * 封装所有投保参与方，包括投保人、被保险人和受益人
 * </p>
 */
public record InsuredPartyList(
                               /*
                                * 清单ID，聚合内唯一
                                */
                               String listId,
                               /*
                                * 投保人信息
                                */
                               HolderInfo holderInfo,
                               /*
                                * 被保险人清单
                                */
                               List<InsuredInfo> insuredList,
                               /*
                                * 受益人清单
                                */
                               List<BeneficiaryInfo> beneficiaryList) {

    /** 受益比例总和校验容差（浮点误差容忍，比例以 1.0 表示 100%） */
    private static final double RATIO_EPSILON = 1e-6;

    /**
     * 校验参与方信息
     * <p>
     * 校验投保人非空、被保险人清单非空，并在存在受益人时校验受益份额之和为 100%。
     * </p>
     *
     * @return 校验结果，true表示校验通过
     */
    public boolean verifyPartyInfo() {
        // 投保人信息不能为空
        if (holderInfo == null) {
            return false;
        }
        // 被保险人清单不能为空且至少有一个被保险人
        if (insuredList == null || insuredList.isEmpty()) {
            return false;
        }
        // 存在受益人时，受益份额之和须为 100%（无受益人则默认法定继承，不校验份额）
        return isBeneficiaryRatioValid();
    }

    /**
     * 受益份额合法性校验：受益人份额之和为 100%（1.0）。
     * <p>
     * 未指定受益人时视为法定继承，不做份额约束返回 {@code true}；指定受益人时每人比例须为正，
     * 且总和等于 1.0（容差 {@link #RATIO_EPSILON}）。份额守恒是寿险身故/满期给付分配的前置不变量。
     * </p>
     *
     * @return 份额合法返回 {@code true}
     */
    public boolean isBeneficiaryRatioValid() {
        if (beneficiaryList == null || beneficiaryList.isEmpty()) {
            return true;
        }
        double sum = 0d;
        for (BeneficiaryInfo beneficiary : beneficiaryList) {
            if (beneficiary.beneficiaryRatio() <= 0d) {
                return false;
            }
            sum += beneficiary.beneficiaryRatio();
        }
        return Math.abs(sum - 1.0d) <= RATIO_EPSILON;
    }

    /**
     * 新增一名被保险人，返回新清单实例（record 不可变，增删返回副本）。
     *
     * @param insured 新增的被保险人
     * @return 追加后的新清单
     */
    public InsuredPartyList addInsured(InsuredInfo insured) {
        List<InsuredInfo> newList = new ArrayList<>(this.insuredList != null ? this.insuredList : List.of());
        newList.add(insured);
        return new InsuredPartyList(this.listId, this.holderInfo, newList, this.beneficiaryList);
    }

    /**
     * 移除指定被保险人，返回新清单实例。移除后至少保留 1 名被保险人，否则抛异常由聚合捕获转业务异常。
     *
     * @param insuredId 被移除的被保险人ID
     * @return 移除后的新清单
     */
    public InsuredPartyList removeInsured(String insuredId) {
        if (this.insuredList == null) {
            throw new IllegalStateException("被保险人清单为空，无法移除");
        }
        List<InsuredInfo> newList = new ArrayList<>(this.insuredList.stream()
                .filter(i -> !i.insuredId().equals(insuredId))
                .toList());
        if (newList.size() == this.insuredList.size()) {
            throw new IllegalArgumentException("被保险人不存在: " + insuredId);
        }
        if (newList.isEmpty()) {
            throw new IllegalStateException("移除后被保险人清单不能为空");
        }
        return new InsuredPartyList(this.listId, this.holderInfo, newList, this.beneficiaryList);
    }

    /**
     * 投保人信息
     */
    public record HolderInfo(
                             /*
                              * 投保人ID
                              */
                             String holderId,
                             /*
                              * 姓名
                              */
                             String name,
                             /*
                              * 证件类型
                              */
                             IdCardType certType,
                             /*
                              * 证件号
                              */
                             String certNo,
                             /*
                              * 联系方式
                              */
                             String phone) {
    }

    /**
     * 被保险人信息
     */
    public record InsuredInfo(
                              /*
                               * 被保险人ID
                               */
                              String insuredId,
                              /*
                               * 姓名
                               */
                              String name,
                              /*
                               * 证件类型
                               */
                              IdCardType certType,
                              /*
                               * 证件号
                               */
                              String certNo,
                              /*
                               * 年龄
                               */
                              int age,
                              /*
                               * 性别
                               */
                              CustomerGender gender,
                              /*
                               * 家庭成员关系（家庭险场景标识与投保人的关系；个险/团单可为 null）
                               */
                              FamilyRelation familyRelation) {
    }

    /**
     * 受益人信息
     */
    public record BeneficiaryInfo(
                                  /*
                                   * 受益人ID
                                   */
                                  String beneficiaryId,
                                  /*
                                   * 姓名
                                   */
                                  String name,
                                  /*
                                   * 证件类型
                                   */
                                  IdCardType certType,
                                  /*
                                   * 证件号
                                   */
                                  String certNo,
                                  /*
                                   * 受益类型
                                   */
                                  InsuranceRole beneficiaryType,
                                  /*
                                   * 受益比例
                                   */
                                  double beneficiaryRatio) {
    }
}
