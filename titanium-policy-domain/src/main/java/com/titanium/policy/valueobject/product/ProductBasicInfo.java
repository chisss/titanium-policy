package com.titanium.policy.valueobject.product;

import com.fasterxml.jackson.annotation.JsonIgnore;

import com.titanium.metadata.enums.insurance.InsuranceProductType;

/**
 * 产品基本信息值对象（防腐）
 * <p>
 * 出单装配时取产品的编码/名称/版本/险种分类，冻结为保单险种段的产品快照。
 * 与 {@link ProductIssueRules} 同置 {@code valueobject/product} 包——它们是<b>领域侧的产品视图</b>，
 * 不是 Port 的组成部分（详见 {@link ProductIssueRules} 的包位置说明）。
 * </p>
 *
 * @param productId      产品ID
 * @param productCode    产品编码
 * @param productName    产品名称
 * @param productVersion 产品版本
 * @param insuranceType  险种三级分类
 * @param status         产品状态码
 */
public record ProductBasicInfo(String productId, String productCode, String productName, String productVersion,
                               InsuranceProductType insuranceType, String status) {

    /** 产品生效状态码 */
    private static final String STATUS_EFFECTIVE = "EFFECTIVE";

    /**
     * 产品是否处于可售状态（生效）。
     *
     * @return 生效返回 {@code true}
     */
    @JsonIgnore
    public boolean isEffective() {
        return STATUS_EFFECTIVE.equals(status);
    }
}
