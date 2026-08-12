package com.titanium.policy.application.orchestration.issuance.assembler;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.titanium.policy.entity.proposal.ProposalLine;
import com.titanium.policy.port.ProductServicePort;
import com.titanium.policy.valueobject.IssuancePlanLine;
import com.titanium.policy.valueobject.IssuanceRequest;
import com.titanium.policy.valueobject.product.ProductBasicInfo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 意向险种段装配器（应用层，三步出单起点的组装步骤）
 * <p>
 * 把出单方案行装配为<b>意向段</b> {@link ProposalLine}——三阶段段结构中最轻的一层。意向阶段
 * 只落「客户想买什么、想保多少」：意向产品、意向保额、意向保费、主附险标识。
 * </p>
 * <p>
 * 不装配核保结论（尚未核保）、条款责任快照（版本未锁定）、完整标的属性（意向阶段仅需简要信息）——
 * 这些在转投保单（{@link InsuranceLineAssembler}）与承保出单（{@link PolicyProductAssembler}）
 * 时逐级精化。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProposalLineAssembler {

    private final ProductServicePort productServicePort;

    /**
     * 装配意向险种段列表。
     * <p>
     * 意向阶段以 {@code lineNo} 表达主附险依附关系（段ID此时才生成，调用方无从引用），
     * 故 {@link ProposalLine#parentLineNo()} 存序号而非段ID——这与投保段/保单段用段ID引用不同。
     * </p>
     *
     * @param request 出单请求
     * @return 意向段列表；无方案行时返回空列表
     */
    public List<ProposalLine> assemble(IssuanceRequest request) {
        List<IssuancePlanLine> planLines = request.planLines();
        if (planLines == null || planLines.isEmpty()) {
            return List.of();
        }
        List<ProposalLine> lines = new ArrayList<>();
        for (IssuancePlanLine planLine : planLines) {
            ProductBasicInfo product = productServicePort
                    .getProductBasicInfo(planLine.productId(), request.tenantId());
            lines.add(new ProposalLine(UUID.randomUUID().toString(), planLine.lineNo(), planLine.productCategory(),
                    planLine.parentLineNo(), planLine.productId(),
                    product != null ? product.productCode() : null,
                    product != null ? product.insuranceType() : request.insuranceType(), planLine.sumInsured(),
                    request.quotedPremium()));
        }
        log.info("意向险种段装配完成: bizNo={}, 段数={}", request.bizNo(), lines.size());
        return List.copyOf(lines);
    }
}
