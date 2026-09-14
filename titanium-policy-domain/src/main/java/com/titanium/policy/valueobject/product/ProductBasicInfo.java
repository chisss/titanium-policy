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
     * <p>
     * ⚠️ <b>当前无调用方，且不应据此在 policy 侧另建在售校验（2026-09-14 m6-907 判定）</b>：
     * 出单必经 product CONFIRM 试算，product 侧 {@code PremiumQuoteApplicationService} 已对非
     * {@code EFFECTIVE} 产品抛 {@code PRICING_PLAN_NOT_EFFECTIVE} <b>同步阻断出单</b>——校验点唯一且强一致。
     * 本方法保留为值对象的领域语义便利方法（判断产品快照状态用），<b>勿</b>新增第二条校验链造成口径分裂。
     * 判定依据见 {@code docs/技术文档/跨域事件目录-2026-09.md} §六.15。
     * </p>
     *
     * @return 生效返回 {@code true}
     */
    @JsonIgnore
    public boolean isEffective() {
        return STATUS_EFFECTIVE.equals(status);
    }
}
