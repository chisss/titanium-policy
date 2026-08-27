package com.titanium.policy.entity.proposal;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

import com.titanium.metadata.enums.insurance.InsuranceProductType;
import com.titanium.metadata.enums.product.ProductEnum.ProductCategory;
import com.titanium.metadata.valueobject.Money;

/**
 * 投保意向险种段实体（意向阶段的 L2，轻量）
 * <p>
 * 客户在 App 上勾选「重疾 + 医疗 + 意外」组合时，<b>意向单阶段就已经是多险种的</b>。改造前
 * {@code ProposalBasicInfo.expectedProductCode} 是<b>单值 String</b>，连「想买主险 + 附加险」
 * 都无法表达，导致三步出单在源头即丢失组合意图。
 * </p>
 * <p>
 * 本类是三阶段段结构中最轻的一层——意向阶段<b>只知道客户想买什么、想保多少</b>：
 * </p>
 * <ul>
 *   <li><b>有</b>：意向产品、意向保额、意向保费、主附险标识；</li>
 *   <li><b>无</b>：核保结论（尚未核保）、条款责任快照（版本未锁定）、精确缴费条件（可能未定）、
 *       完整标的属性（意向阶段仅需简要信息，见 {@link ProposalSubject}）。</li>
 * </ul>
 * <p>
 * 转投保单时精化为 {@code InsuranceLine}（补试算保费与完整标的），承保时再精化为
 * {@code PolicyProduct}（补条款责任快照与承保值）。三者是渐进精化关系，不共用类型——
 * 共用会使承保段充满「仅意向阶段有意义」的空字段。
 * </p>
 *
 * @param lineId             段ID（意向单内唯一）
 * @param lineNo             段序号
 * @param productCategory    产品类别（MAIN 主险 / RIDER 附加险）
 * @param parentLineNo       依附的主险段序号（RIDER 用；意向阶段段ID可能未定，故用序号关联）
 * @param productId          意向产品ID（客户仅表达险种意图时可空）
 * @param productCode        意向产品编码
 * @param insuranceType      险种三级分类
 * @param intendedSumInsured 意向保额（客户期望值，非试算值）
 * @param intendedPremium    意向保费（客户预算或粗略报价）
 */
public record ProposalLine(String lineId, int lineNo, ProductCategory productCategory, Integer parentLineNo,
                           String productId, String productCode, String productVersion, InsuranceProductType insuranceType,
                           Money intendedSumInsured, Money intendedPremium) {

    /** 兼容产品版本字段加入前的历史事件和既有调用方。 */
    public ProposalLine(String lineId, int lineNo, ProductCategory productCategory, Integer parentLineNo,
                        String productId, String productCode, InsuranceProductType insuranceType,
                        Money intendedSumInsured, Money intendedPremium) {
        this(lineId, lineNo, productCategory, parentLineNo, productId, productCode, null, insuranceType,
                intendedSumInsured, intendedPremium);
    }

    /**
     * 解析单据级险种分类：显式值优先，否则仅从唯一主险段派生。
     *
     * @param explicitType 显式单据级险种分类
     * @param lines 意向险种段
     * @return 解析后的险种分类；主险段不唯一或没有分类时返回 null
     */
    public static InsuranceProductType resolveInsuranceType(InsuranceProductType explicitType,
                                                              List<ProposalLine> lines) {
        if (explicitType != null || lines == null) {
            return explicitType;
        }
        ProposalLine mainLine = null;
        for (ProposalLine line : lines) {
            if (line == null || !line.isMain()) {
                continue;
            }
            if (mainLine != null) {
                return null;
            }
            mainLine = line;
        }
        return mainLine != null ? mainLine.insuranceType() : null;
    }

    /**
     * 是否为主险段。
     *
     * @return 主险返回 {@code true}
     */
    @JsonIgnore
    public boolean isMain() {
        return productCategory == ProductCategory.MAIN;
    }

    /**
     * 是否为附加险段。
     *
     * @return 附加险返回 {@code true}
     */
    @JsonIgnore
    public boolean isRider() {
        return productCategory == ProductCategory.RIDER;
    }

    /**
     * 意向信息是否完整到可转投保单（须有产品标识）。
     *
     * @return 可转换返回 {@code true}
     */
    @JsonIgnore
    public boolean isConvertible() {
        return (productId != null && !productId.isBlank()) || (productCode != null && !productCode.isBlank());
    }
}
