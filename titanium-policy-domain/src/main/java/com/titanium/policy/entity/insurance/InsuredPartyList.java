package com.titanium.policy.entity.insurance;

import java.util.List;

import com.titanium.metadata.enums.customer.CustomerEnum.CustomerGender;
import com.titanium.metadata.enums.customer.CustomerEnum.IdCardType;
import com.titanium.metadata.enums.customer.CustomerEnum.InsuranceRole;

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

    /**
     * 校验参与方信息
     * <p>
     * 调用客户域校验投保人/被保险人身份、资质合法性
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
        // 调用客户域校验投保人/被保险人身份、资质合法性
        // 暂时返回true
        return true;
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
                              CustomerGender gender) {
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
