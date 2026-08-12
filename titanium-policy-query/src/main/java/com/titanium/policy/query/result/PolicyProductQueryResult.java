package com.titanium.policy.query.result;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import lombok.Data;

/**
 * 保单险种段查询结果（L2，读侧对外契约）
 * <p>
 * 一张保单返回 1..N 个段——这是「一单多险」在读侧的呈现：寿险的「终身寿主险 + 附加重疾 +
 * 附加医疗」、车险的「交强险 + 车损险 + 三者险」各自一项，每项含独立的保额、保费、保障期间、
 * 缴费条件、核保结论与承保状态。
 * </p>
 * <p>
 * 段内嵌套条款（L2.5）、标的（L3）、责任（L4）三层明细，供保单全景查询一次返回完整构成。
 * 仅查段本身时三个列表为空，由查询服务按需装配。
 * </p>
 */
@Data
public class PolicyProductQueryResult {

    /** 保单ID */
    private String                          policyId;

    /** 险种段ID（保单内唯一） */
    private String                          policyProductId;

    /** 段序号（对应出单请求的 planLine 序号） */
    private Integer                         lineNo;

    /** 产品类别码（MAIN 主险 / RIDER 附加险） */
    private String                          productCategory;

    /** 依附的主险段ID（附加险有值） */
    private String                          parentPolicyProductId;

    /** 产品ID（指向 product 域） */
    private String                          productId;

    /** 产品编码 */
    private String                          productCode;

    /** 产品名称 */
    private String                          productName;

    /** 产品版本（锁定出单时点定义） */
    private String                          productVersion;

    /** 险种三级分类码 */
    private String                          insuranceType;

    /** 本险种保额 */
    private BigDecimal                      sumInsured;

    /** 本险种保费（拒保段不计入保单总保费） */
    private BigDecimal                      premium;

    /** 币种 */
    private String                          currency;

    /** 本险种保障起期（可独立于保单主期间） */
    private LocalDateTime                   periodStart;

    /** 本险种保障止期（终身型为空） */
    private LocalDateTime                   periodEnd;

    /** 保障期间类型码（FIXED_TERM/WHOLE_LIFE/CUSTOM） */
    private String                          periodType;

    /** 本险种缴费频率码（缴费期 ≠ 保障期） */
    private String                          paymentFrequency;

    /** 本险种缴费年数 */
    private Integer                         premiumPaymentYears;

    /** 本险种核保结论码（ACCEPT/MODIFY/REJECT/POSTPONE） */
    private String                          underwritingConclusion;

    /** 本险种承保状态码（UNDERWRITING/ACCEPTED/EFFECTIVE/REJECTED/SURRENDERED/EXPIRED） */
    private String                          lineStatus;

    /** 本段绑定的条款快照（L2.5；仅查段时为空列表） */
    private List<PolicyClauseQueryResult>   clauses;

    /** 本段承保的标的（L3；仅查段时为空列表） */
    private List<PolicySubjectQueryResult>  subjects;

    /** 本段的保险责任（L4；仅查段时为空列表） */
    private List<PolicyCoverageQueryResult> coverages;
}
