package com.titanium.policy.entity.proposal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.titanium.metadata.enums.insurance.InsuranceProductType;
import com.titanium.metadata.enums.product.ProductEnum.ProductCategory;

/**
 * 意向段单据级险种分类派生规则测试（G11/g11-04 ⑤）。
 * <p>
 * 旧行为「主险不唯一即返回 null」会把交强 + 商业同单（同属 AUTO）的单据级险种打成空；
 * 新判据只看各主险段分类是否一致。
 * </p>
 */
class ProposalLineTest {

    /** 正例：两个主险段同险种分类（交强 + 商业 同属 AUTO）⇒ 派生该分类。 */
    @Test
    void derivesInsuranceTypeWhenAllMainLinesShareSameType() {
        assertEquals(InsuranceProductType.AUTO, ProposalLine.resolveInsuranceType(null, List.of(
                mainLine(InsuranceProductType.AUTO), mainLine(InsuranceProductType.AUTO))));
    }

    /** 反例：主险段分类不一致（车险 + 企财险）⇒ 单据级单一字段表达不了，返回 null 由调用方显式声明。 */
    @Test
    void returnsNullWhenMainLinesDisagreeOnInsuranceType() {
        assertNull(ProposalLine.resolveInsuranceType(null, List.of(
                mainLine(InsuranceProductType.AUTO), mainLine(InsuranceProductType.ENTERPRISE_PROPERTY))));
    }

    /** 显式单据级分类优先，逐段派生不参与。 */
    @Test
    void keepsExplicitTopLevelType() {
        assertEquals(InsuranceProductType.AUTO, ProposalLine.resolveInsuranceType(InsuranceProductType.AUTO,
                List.of(mainLine(InsuranceProductType.ENTERPRISE_PROPERTY))));
    }

    /** 任一主险段分类缺失 ⇒ 返回 null，不得忽略缺失段只按其余段派生。 */
    @Test
    void returnsNullWhenAnyMainLineTypeIsMissing() {
        assertNull(ProposalLine.resolveInsuranceType(null, List.of(
                mainLine(InsuranceProductType.AUTO), mainLine(null))));
    }

    /** 仅附加险段 ⇒ 不派生（单据级分类只看主险段，附加险分类不参与）。 */
    @Test
    void ignoresRiderLinesWhenDerivingType() {
        ProposalLine rider = new ProposalLine("LINE_R", 1, ProductCategory.RIDER, null, null, null,
                InsuranceProductType.AUTO, null, null);
        assertNull(ProposalLine.resolveInsuranceType(null, List.of(rider)));
    }

    private static ProposalLine mainLine(InsuranceProductType type) {
        return new ProposalLine("LINE_1", 1, ProductCategory.MAIN, null, null, null, type, null, null);
    }
}
