package com.titanium.policy.query.result;

import java.util.List;

import lombok.Data;

/**
 * 保单全景查询结果（读侧对外契约）
 * <p>
 * <b>一次返回一张保单的全部维度</b>，回答后台「拿到保单号能看清什么」这一问题：
 * </p>
 * <ol>
 *   <li>这是什么<b>保险产品（险种）</b>、绑定哪些<b>条款</b>（含版本）→ {@link #lines} 内的产品与条款；</li>
 *   <li>有哪些<b>保险责任</b>（保额/免赔/赔付比例/等待期）→ {@link #lines} 内的责任明细；</li>
 *   <li>承保了哪些<b>标的</b> → {@link #lines} 内的标的明细；</li>
 *   <li><b>投保人 / 被保险人 / 受益人</b>分别是谁（含受益顺位与份额）→ {@link #policy} 的投保人ID
 *       + {@link #insuredList} + {@link #beneficiaryList}；</li>
 *   <li>通过什么方式<b>收了多少钱</b> → {@link #collection}；</li>
 *   <li><b>保障期限</b>区间、等待期与犹豫期届满日 → {@link #policy} 的期间字段；</li>
 *   <li><b>保单状态与核保状态</b> → {@link #policy}；</li>
 *   <li>是否已生成<b>电子保单</b> → {@link #policy} 的单证字段；</li>
 *   <li>保单从哪个<b>渠道</b>过来 → {@link #policy} 的渠道字段。</li>
 * </ol>
 * <p>
 * 读侧由 {@code PolicyLineQueryService} 聚合七张读模型表装配（保单主体 + 险种段 + 条款 + 标的 +
 * 责任 + 被保险人 + 受益人 + 收费），单险种保单的 {@link #lines} 长度为 1。
 * </p>
 */
@Data
public class PolicyFullDetailQueryResult {

    /** 保单主体（含状态/期间/渠道/单证/主险冗余字段） */
    private PolicyQueryResult              policy;

    /** 险种段列表（L2，段内嵌套条款/标的/责任三层明细；单险种为 1 项） */
    private List<PolicyProductQueryResult> lines;

    /** 被保险人清单（投保人在 {@link #policy} 的 policyHolderId 上） */
    private List<PolicyInsuredQueryResult>     insuredList;

    /** 受益人清单（含受益类型、受益顺位与份额） */
    private List<PolicyBeneficiaryQueryResult> beneficiaryList;

    /** 收费信息（收费方式/账单/支付单/应收实收/收讫状态） */
    private PolicyCollectionQueryResult    collection;

    /**
     * 险种段数量（单险种为 1，一单多险 > 1）。
     *
     * @return 段数量
     */
    public int lineCount() {
        return lines != null ? lines.size() : 0;
    }

    /**
     * 主险段（一张保单有且仅有一个）。
     *
     * @return 主险段；无段时返回 null
     */
    public PolicyProductQueryResult mainLine() {
        if (lines == null) {
            return null;
        }
        return lines.stream()
                .filter(line -> "MAIN".equals(line.getProductCategory()))
                .findFirst()
                .orElse(null);
    }

    /**
     * 全部保险责任（跨险种段扁平化，供理赔域一次取全定责依据）。
     *
     * @return 责任列表
     */
    public List<PolicyCoverageQueryResult> allCoverages() {
        if (lines == null) {
            return List.of();
        }
        return lines.stream()
                .filter(line -> line.getCoverages() != null)
                .flatMap(line -> line.getCoverages().stream())
                .toList();
    }
}
